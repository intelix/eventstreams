/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eventstreams.gauges

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{Actor, ActorRef}
import akka.cluster.Cluster
import com.typesafe.config.Config
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors.{ActorWithTicks, AutoAcknowledgingService, BaseActorSysevents, RouteeActor}
import eventstreams.signals._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._


trait GaugesManagerSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "Gauges.Manager"

  val MetricAdded = 'TestEvent.info

}

object GaugesManagerConstants extends GaugesManagerSysevents {
  val id = "gauges"
}


case class LevelUpdateNotification(key: SignalKey, level: Int)

class GaugesManagerActor(sysconfig: Config, cluster: Cluster)
  extends AutoAcknowledgingService[EventFrame]
  with RouteeActor
  with ActorWithTicks
  with NowProvider
  with GaugesManagerSysevents
  with WithSyseventPublisher {

  case class MetricMeta(id: String, actor: ActorRef, priority: String = "M", signalType: SigMetricType, level: Int = 0)

  case class FilterPayload(selector: Selector, payload: JsValue)


  class Selector(data: JsValue) {

    class SelectedGroupLevelItems(val items: Seq[String])

    class GroupLevel(val selection: SelectedGroupLevelItems, val query: String) {
      def this() = this(new SelectedGroupLevelItems(Seq()), "*")
    }

    class Group(val levels: Seq[GroupLevel])

    val maxMatching = data +> 'limm | 20
    val maxSelected = data +> 'lims | 50

    val warningLevel = data +> 'lvl | 0
    val limit = data +> 'lim match {
      case Some(i) if i < 2000 => i
      case _ => 2000
    }

    val groups: Map[Symbol, Group] = Seq('h, 's, 'c, 'm).map { k => k -> jsonToGroup(data #> k) }.toMap

    private def jsonToGroupItemSelection(j: Option[JsValue]): SelectedGroupLevelItems = {
      val seq = j.flatMap(_.asOpt[JsArray].map(_.value.toSeq.map(_.asOpt[String] | "").filterNot(_.isEmpty)))
      new SelectedGroupLevelItems(seq | Seq[String]())
    }

    private def jsonToGroup(maybeJ: Option[JsValue]): Group =
      maybeJ.map { j =>
        new Group(j.asOpt[JsArray].map(_.value.map { j1 =>
          new GroupLevel(jsonToGroupItemSelection(j1 #> 's), j1 ~> 'q | "*")
        }) | Seq())
      } | new Group(Seq[GroupLevel](new GroupLevel()))

    def isIncluded(v: (SignalKey, MetricMeta)): Boolean = v._2.level >= warningLevel && isSelected(v._1)

    def isSelected(s: SignalKey): Boolean = !groups.exists { case (sym, g) => !groupIsSelected(g, extractors(sym)(s)) }

    def groupIsSelected(selected: Group, actual: Seq[String]): Boolean =
      !selected.levels.zip(actual).exists {
        case (gl, i) => gl.selection.items.nonEmpty && !gl.selection.items.contains(i)
      }

    def queryMatchFunction(name: String, groupLevel: Option[GroupLevel]) = groupLevel match {
      case Some(gl) => gl.query match {
        case "*" => true
        case "" => true
        case x if x == name => true
        case x if x.startsWith("*") && x.endsWith("*") => name.contains(x.substring(1, x.length - 1))
        case x if x.startsWith("*") => name.endsWith(x.substring(1))
        case x if x.endsWith("*") => name.startsWith(x.substring(0, x.length - 1))
        case _ => false
      }
      case _ => true
    }

    def isSelectedItem(name: String, groupLevel: Option[GroupLevel]) = groupLevel match {
      case Some(gl) => gl.selection.items.contains(name)
      case _ => false
    }

    private def toSelectorGroups(sortedMetrics: List[(SignalKey, MetricMeta)]) =
      sortedMetrics.foldLeft[SelectorResult](new SelectorResult(maxMatching, maxSelected)) {
        case (m, (k, _)) =>
          extractors.foreach {
            case (s, extractor) =>
              val extractedLevels = extractor(k)
              val selectedLevels = groups(s).levels

              val isMatchingMetric = isSelected(k)

              @tailrec
              def process(idx: Int): Unit =
                if (idx < extractedLevels.size) {
                  val nextItem = extractedLevels(idx)
                  val selectedLevel = if (selectedLevels.size <= idx) None else Some(selectedLevels(idx))
                  val levelItems = m.levelItemsFor(s, idx)
                  val selectedItem = isSelectedItem(nextItem, selectedLevel)
                  val matchingQuery = queryMatchFunction(nextItem, selectedLevel)
                  if (selectedItem || (matchingQuery && (isMatchingMetric || selectedLevel.exists(_.selection.items.nonEmpty)))) {
                    levelItems.add(nextItem, selectedItem, matchingQuery)
                    if (selectedItem) process(idx + 1)
                  }
                }

              process(0)
          }
          m
      }.toJson

    private def toIncludedMetrics(sortedMetrics: List[(SignalKey, MetricMeta)]) =
      sortedMetrics.view.filter(isIncluded).take(limit).toSeq.map { i => Json.arr(i._2.id, i._2.signalType.id, i._1.toMetricName) }

    def toPayload(sortedMetrics: List[(SignalKey, MetricMeta)]) =
      Json.obj(
        "s" -> toSelectorGroups(sortedMetrics),
        "m" -> toIncludedMetrics(sortedMetrics)
      )

  }

  type Extractor = SignalKey => Seq[String]
  val extractors = Map[Symbol, Extractor]('h -> (_.locationAsSeq), 's -> (_.systemAsSeq), 'c -> (_.componentAsSeq), 'm -> (_.metricAsSeq))

  class SelectorResult(maxMatching: Int, maxSelected: Int) {
    val groups: Map[Symbol, mutable.Buffer[LevelItems]] = Map(
      'h -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
      's -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
      'c -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
      'm -> mutable.Buffer(new LevelItems(mutable.ListBuffer()))
    )

    class VisibleItem(val name: String, val selected: Boolean, val matchesQuery: Boolean)

    class LevelItems(val items: mutable.ListBuffer[VisibleItem]) {
      def this() = this(mutable.ListBuffer())

      def add(name: String, selected: Boolean, matchesQuery: Boolean): Unit =
        if (!items.exists(_.name == name)) {
          items += new VisibleItem(name, selected, matchesQuery)
        }

      def limitedSet = items.filter(_.selected) ++ items.filter(!_.selected).take(maxMatching)

      def toJson = Json.obj(
        "i" -> Json.toJson(limitedSet.take(maxSelected).map { i =>
          Json.obj(
            "n" -> i.name,
            "q" -> i.matchesQuery
          )
        }.toArray),
        "c" -> items.size
      )

    }

    def levelItemsFor(s: Symbol, idx: Int): LevelItems =
      groups.get(s).get match {
        case x if x.length <= idx =>
          x += new LevelItems()
          x(idx)
        case x => x(idx)
      }

    def toJson = Json.obj(
      "h" -> groups('h).map(_.toJson),
      "s" -> groups('s).map(_.toJson),
      "c" -> groups('c).map(_.toJson),
      "m" -> groups('m).map(_.toJson)
    )
  }

  var firstChangeInMetrics: Option[Long] = None

  var liveMetrics: Map[SignalKey, MetricMeta] = Map()

  var liveMetricFilters: Map[TopicKey, FilterPayload] = Map()

  var metricUpdatePublishWaitPeriodMs = 3000

  override def canAccept(count: Int): Boolean = true

  override def onNext(e: EventFrame): Unit = onNextSignal(e)

  override def key: ComponentKey = GaugesManagerConstants.id


  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  //
  //  override def onCommand(maybeData: Option[JsValue]): CommandHandler = cmdHandler(maybeData) orElse super.onCommand(maybeData)
  //
  //
  //  private def cmdHandler(maybeData: Option[JsValue]): CommandHandler = {
  //    case T_METRIC_FILTER => OK(metricFilter(maybeData | Json.obj()))
  //  }


  override def onSubscribe: SubscribeHandler = {
    case topic@TopicWithPrefix("mfilter", data) => rememberFilter(topic, data)
  }

  override def onUnsubscribe: UnsubscribeHandler = {
    case topic@TopicWithPrefix("mfilter", _) => forgetFilter(topic)
  }

  private def forgetFilter(topic: TopicKey) =
    liveMetricFilters -= topic

  private def filterPayload(sortedMetrics: List[(SignalKey, MetricMeta)], data: String) = {
    val selector = new Selector(Json.parse(data))
    val payload = selector.toPayload(sortedMetrics)
    FilterPayload(selector, payload)
  }

  private def rememberFilter(topic: TopicKey, data: String) =
    liveMetricFilters.get(topic) match {
      case None =>
        val fp = filterPayload(sortedMetrics, data)
        liveMetricFilters += topic -> fp
        topic !!* fp.payload
      case Some(p) => topic !!* p.payload
    }


  private def handler: Receive = {
    case LevelUpdateNotification(key, l) => updateLevel(key, l)
  }

  private def updateLevel(key: SignalKey, l: Int) = liveMetrics.get(key) foreach { m =>
    liveMetrics += key -> m.copy(level = l)
    firstChangeInMetrics = firstChangeInMetrics match {
      case None => Some(now)
      case _ => firstChangeInMetrics
    }
  }

  private def rebuildFilters() = {
    val sm = sortedMetrics
    liveMetricFilters.map {
      case current@(topicKey@TopicWithPrefix(_, data), payload) =>

        filterPayload(sm, data) match {
          case newFp if newFp.payload != payload.payload =>
            topicKey !!* newFp.payload
            topicKey -> newFp
          case newFp =>
            current
        }
    }
  }

  def sortLogic(k1: (SignalKey, MetricMeta), k2: (SignalKey, MetricMeta)): Boolean =
    k1._2.priority < k2._2.priority || (k1._2.priority == k2._2.priority && k1._1.toMetricName < k2._1.toMetricName)

  def sortedMetrics = liveMetrics.toList.sortWith(sortLogic)


  override def processTick(): Unit = {
    firstChangeInMetrics = firstChangeInMetrics match {
      case Some(x) if now - x > metricUpdatePublishWaitPeriodMs =>
        rebuildFilters()
        None
      case x => x
    }
    super.processTick()
  }

  private def onNextSignal(e: SignalEventFrame) = destinationFor(e) foreach (_ ! e)

  private def destinationFor(e: SignalEventFrame): Option[ActorRef] =
    e.sigKey.map(destinationForKey(_, e))

  private def destinationForKey(sigKey: SignalKey, e: SignalEventFrame): ActorRef = liveMetrics.getOrElse(sigKey, addMetric(sigKey, e)).actor


  private def addMetric(sigKey: SignalKey, e: SignalEventFrame): MetricMeta = {
    val id = (key / liveMetrics.size.toString).key
    val sigType = e.sigMetricType | SigMetricTypeGauge()
    val ref = context.actorOf(GaugeActor(sigType, id, sigKey))
    val meta = MetricMeta(id, ref, e.sigPriority, sigType)
    liveMetrics += sigKey -> meta

    MetricAdded >>('Key -> sigKey.toMetricName, 'ComponentKey -> id)

    firstChangeInMetrics = firstChangeInMetrics match {
      case None => Some(now)
      case _ => firstChangeInMetrics
    }

    meta
  }
}

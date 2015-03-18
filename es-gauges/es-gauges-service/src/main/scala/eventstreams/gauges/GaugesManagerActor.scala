package eventstreams.gauges

import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.configHelper
import eventstreams.core.actors.{BaseActorSysevents, AutoAcknowledgingService, RouteeActor}
import eventstreams.signals._
import eventstreams.{ComponentKey, EventFrame, OK, TopicKey}
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._


trait GaugesManagerSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "Gauges.Manager"

  val TestEvent = 'TestEvent.info

}

object GaugesManagerConstants extends GaugesManagerSysevents {
  val id = "gauges"



}


class GaugesManagerActor(sysconfig: Config, cluster: Cluster)
  extends AutoAcknowledgingService[EventFrame]
  with RouteeActor
  with GaugesManagerSysevents
  with WithSyseventPublisher {

  case class MetricMeta(id: String, actor: ActorRef, priority: String = "M", level: Int = 0)

  var liveMetrics: Map[SignalKey, MetricMeta] = Map()

  val T_METRIC_FILTER = TopicKey("mfilter")


  override def canAccept(count: Int): Boolean = true

  override def onNext(e: EventFrame): Unit = onNextSignal(e)

  override def key: ComponentKey = GaugesManagerConstants.id


  override def onCommand(maybeData: Option[JsValue]): CommandHandler = cmdHandler(maybeData) orElse super.onCommand(maybeData)


  private def cmdHandler(maybeData: Option[JsValue]): CommandHandler = {
    case T_METRIC_FILTER => OK(metricFilter(maybeData | Json.obj()))
  }

  private def metricFilter(data: JsValue): JsValue = {

    val maxMatching = 3
    val maxSelected = 50

    class SelectedGroupLevelItems(val items: Seq[String])
    class GroupLevel(val selection: SelectedGroupLevelItems, val query: String)
    class Group(val levels: Seq[GroupLevel])
    class Selector(val groups: Map[Symbol, Group])

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
    class SelectorResult(val groups: Map[Symbol, mutable.Buffer[LevelItems]]) {
      def this() = this(Map(
        'h -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
        's -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
        'c -> mutable.Buffer(new LevelItems(mutable.ListBuffer())),
        'm -> mutable.Buffer(new LevelItems(mutable.ListBuffer()))
      ))

      def toJson = Json.obj(
        "h" -> groups('h).map(_.toJson),
        "s" -> groups('s).map(_.toJson),
        "c" -> groups('c).map(_.toJson),
        "m" -> groups('m).map(_.toJson)
      )
    }

    type MatchedMetrics = Seq[SignalKey]
    type Result = (SelectorResult, MatchedMetrics)

    def jsonToGroupItemSelection(j: Option[JsValue]): SelectedGroupLevelItems = {
      val seq = j.flatMap(_.asOpt[JsArray].map(_.value.toSeq.map(_.asOpt[String] | "").filterNot(_.isEmpty)))
      new SelectedGroupLevelItems(seq | Seq[String]())
    }

    def jsonToGroup(maybeJ: Option[JsValue]): Group =
      maybeJ.map { j =>
        new Group(j.asOpt[JsArray].map(_.value.map { j1 =>
          new GroupLevel(jsonToGroupItemSelection(j1 #> 's), j1 ~> 'q | "*")
        }) | Seq())
      } | new Group(Seq[GroupLevel]())

    var limit = data +> 'lim match {
      case Some(i) if i < 2000 => i
      case _ => 2000
    }

    var warningLevel = data +> 'lvl | 0

    val selector: Selector = new Selector(Seq('h, 's, 'c, 'm).map { k => k -> jsonToGroup(data #> k) }.toMap)

    println(s"!>>>>> data: $data")
    println(s"!>>>>> selector: $selector")

    type Extractor = SignalKey => Seq[String]
    val extractors = Map[Symbol, Extractor]('h -> (_.locationAsSeq), 's -> (_.systemAsSeq), 'c -> (_.componentAsSeq), 'm -> (_.metricAsSeq))

    def sortLogic(k1: (SignalKey, MetricMeta), k2: (SignalKey, MetricMeta)): Boolean =
      k1._2.priority < k2._2.priority || ( k1._2.priority == k2._2.priority && k1._1.toMetricName < k2._1.toMetricName )

    val sortedMetrics = liveMetrics.toList.sortWith(sortLogic)

    def isIncluded(v: (SignalKey, MetricMeta)): Boolean = v._2.level >= warningLevel && isSelected(v._1)
    def isSelected(s: SignalKey): Boolean = !selector.groups.exists { case (sym, g) => !groupIsSelected(g, extractors(sym)(s)) }
    def groupIsSelected(selected: Group, actual: Seq[String]): Boolean =
      !selected.levels.zip(actual).exists {
        case (gl, i) => gl.selection.items.nonEmpty && !gl.selection.items.contains(i)
      }

    val includedMetrics = sortedMetrics.view.filter(isIncluded).take(limit)

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
      case _ => false
    }
    def isSelectedItem(name: String, groupLevel: Option[GroupLevel]) = groupLevel match {
      case Some(gl) => gl.selection.items.contains(name)
      case _ => false
    }


    val sr = sortedMetrics.foldLeft[SelectorResult](new SelectorResult()) {
      case (m, (k, _)) =>
        extractors.foreach {
          case (s, extractor) =>
            val addedLevels = m.groups.get(s).get
            val extractedLevels = extractor(k)
            val selectedLevels = selector.groups(s).levels

            val isMatchingMetric = isSelected(k)

            @tailrec
            def process(idx: Int): Unit =
              if (idx <= extractedLevels.size) {
                val nextItem = extractedLevels(idx)
                val selectedLevel = if (selectedLevels.size <= idx) None else Some(selectedLevels(idx))
                if (addedLevels.size <= idx) addedLevels += new LevelItems()
                val selectedItem = isSelectedItem(nextItem, selectedLevel)
                val matchingQuery = queryMatchFunction(nextItem, selectedLevel)
                if (selectedItem || (matchingQuery && (isMatchingMetric || selectedLevel.exists(_.selection.items.nonEmpty)))) {
                  addedLevels(idx).add(nextItem, selectedItem, matchingQuery)
                  if (selectedItem) process(idx + 1)
                }
              }

            process(0)
        }
        m
    }


    val includedMetricsJson = includedMetrics.toSeq.map { i => Json.arr(i._2.id, i._1.toMetricName)}

    Json.obj(
      "s" -> sr.toJson,
      "m" -> includedMetricsJson
    )

  }

  private def onNextSignal(e: SignalEventFrame) = destinationFor(e) foreach (_ ! e)

  private def destinationFor(e: SignalEventFrame): Option[ActorRef] =
    e.sigKey.map(destinationForKey(_, e))

  private def destinationForKey(sigKey: SignalKey, e: SignalEventFrame): ActorRef = liveMetrics.getOrElse(sigKey, addMetric(sigKey, e)).actor

  private def addMetric(sigKey: SignalKey, e: SignalEventFrame): MetricMeta = {
    val id = (key / liveMetrics.size.toString).key
    val ref = context.actorOf(Props(new GaugeActor(id, sigKey)))
    val meta = MetricMeta(id, ref, e.sigPriority)
    liveMetrics += sigKey -> meta

    TestEvent >> ('Key -> sigKey, 'Meta -> meta, 'Id -> id)

    meta
  }
}

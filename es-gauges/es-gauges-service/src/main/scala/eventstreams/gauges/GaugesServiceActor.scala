package eventstreams.gauges

import akka.cluster.Cluster
import com.typesafe.config.Config
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{ActorWithPeriodicalBroadcasting, ActorWithTicks, AutoAcknowledgingService, RouteeActor}
import eventstreams.signals._
import eventstreams.{ComponentKey, EventFrame, TopicKey}
import play.api.libs.json.{JsArray, JsString, JsValue, Json}

import scalaz.Scalaz._

trait GaugesServiceSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Gauges.Service"
}

object GaugesServiceConstants extends GaugesServiceSysevents {
  val id = "gauges"
}

class GaugesServiceActor(sysconfig: Config, cluster: Cluster)
  extends AutoAcknowledgingService[EventFrame]
  with ActorWithPeriodicalBroadcasting
  with RouteeActor
  with ActorWithTicks
  with GaugesServiceSysevents
  with WithSyseventPublisher {

  val T_DICT = TopicKey("dict_snapshot")
  private val ValuesTopicId = "values:(.+)".r
  private var buckets: Map[SignalKey, Bucket] = Map()
  private var scheduledForUpdate: Map[SignalKey, Bucket] = Map()
  private var scheduledForDictUpdate: Map[SignalKey, Bucket] = Map()
  private var uidCounter: Long = 0

  private var broadcastList: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List()

  override def key: ComponentKey = ComponentKey(GaugesServiceConstants.id)

  override def onSubscribe: SubscribeHandler = super.onSubscribe orElse {
    case T_DICT => publishDict()
    case TopicKey(key) =>
      publishFor(key, buckets)
      broadcastList = broadcastList :+(TopicKey(key), 30, () => payloadFor(key, buckets), TopicKey(key) !!* _)
  }


  override def onUnsubscribe: UnsubscribeHandler = super.onUnsubscribe orElse {
    case TopicKey(key) =>
      broadcastList = broadcastList.filterNot { case (k, _, _, _) => k.key == key }
  }

  override def canAccept(count: Int): Boolean = true

  override def onNext(e: EventFrame): Unit = ()
//  processNext(e)

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = broadcastList


  override def processTick(): Unit = {
    super.processTick()
    if (scheduledForUpdate.nonEmpty) {
      allTopicKeys.foreach { t => publishFor(t.key, scheduledForUpdate) }
      scheduledForUpdate = Map()
    }
    if (scheduledForDictUpdate.nonEmpty) {
      publishDict()
      scheduledForDictUpdate = Map()
    }
  }

  private def publishDict() = T_DICT !! dict

  private def bucketToDict(b: Bucket) = Json.obj(
    "id" -> b.uid,
    "group" -> (b.id.system | ""),
//    "subgroup" -> (b.id.subgroup | ""),
//    "sensor" -> b.id.sensor,
    "location" -> (b.id.location | ""),
    "levels" -> (b.levels.map { l => l.yellow + "," + l.red } | ""),
    "unit" -> (b.unit | ""),
    "type" -> b.metric.id,
    "srate" -> (b.samplingRate | 0),
    "ranges" -> b.ranges
  )

  private def dict: Option[JsValue] = {
    Some(JsArray(buckets.values.map(bucketToDict).toSeq))
  }

  private def scheduleUpdate(b: Bucket) = scheduledForUpdate += b.id -> b
  private def scheduleDictUpdate(b: Bucket) = scheduledForDictUpdate += b.id -> b

  private def publishFor(key: String, source: Map[SignalKey, Bucket]) = payloadFor(key, source).foreach(TopicKey(key) !!* _)

  private def payloadFor(key: String, source: Map[SignalKey, Bucket]) = key match {
    case ValuesTopicId(x) => dataFor(source, x, valuesExtractor)
    case _ => None
  }

  private def valuesExtractor(b: Bucket): String = b.uid + ":" + b.currentValues.map("%.2f" format _).mkString(",")

  private def dataFor(source: Map[SignalKey, Bucket], query: String, extractor: (Bucket) => String): Option[JsValue] =
    Some(JsString(
      source.collect {
        case (i, b) if i.matchesQuery(query) => extractor(b)
      }.mkString(";")))

//  private def processNext(s: SignalEventFrame): Unit =
//    s.sensor.flatMap { sensor =>
//      val id = SignalKey(sensor, s)
//
//      buckets.get(id) match {
//        case x @ Some(_) => x
//        case None =>
//          uidCounter = uidCounter + 1
//          for (
//            b <- BucketBuilder(uidCounter, id, s)
//          ) yield {
//            buckets = buckets + (id -> b)
//            scheduleDictUpdate(b)
//            b
//          }
//      }
//    }.foreach { b =>
//      if (b.updateMeta(s)) scheduleDictUpdate(b)
//      if (b.updateReading(s)) scheduleUpdate(b)
//    }

}

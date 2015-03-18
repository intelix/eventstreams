package eventstreams.gauges

import eventstreams.signals._

import eventstreams.{TopicKey, EventFrame, ComponentKey}
import eventstreams.core.actors.{ActorWithTicks, RouteeActor}

class GaugeActor(id: String, signalKey: SignalKey)
  extends RouteeActor
  with ActorWithTicks {

  private val T_DATA = TopicKey("data")

  private var bucket: Option[GaugeBucket] = None

  override def key: ComponentKey = id
  override def componentId: String = "Metric." + signalKey.toMetricName


  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
  }


  private def update(s: SignalEventFrame) = {
//    val bucket = bucketFor(s)
//    bucket.update(s)
//    T_DATA !! bucket.toData
  }



//  private def bucketFor(s: SignalEventFrame): GaugeBucket = bucket match {
//    case Some(b) => b
//    case None =>
//      val b = createBucketFor(s)
//      bucket = Some(b)
//      b
//  }

  private def createBucketFor(s: SignalEventFrame) = {}

  private def handler: Receive = {
    case e: SignalEventFrame => update(e)
  }

}

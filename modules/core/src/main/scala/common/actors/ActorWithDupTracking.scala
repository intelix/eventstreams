package common.actors

import akka.actor.ActorRef
import common.NowProvider

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

case class LastMessage(ref: ActorRef, ts: Long, lastId: Option[Long])

trait ActorWithDupTracking extends ActorWithTicks with NowProvider {

  private val lastMessageTrackingMap = mutable.Map[ActorRef, LastMessage]()
  private var maintenanceTs = 0L

  def isDup(ref: ActorRef, id: Long) =
    lastMessageTrackingMap.getOrElse(ref, LastMessage(ref, 0, None)) match {
      case LastMessage(_, _, Some(lastId)) if lastId >= id => true
      case e =>
        lastMessageTrackingMap += ref -> e.copy(ts = now, lastId = Some(id))
        false
    }


  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    if (now - maintenanceTs > 30000) {
      lastMessageTrackingMap.collect {
        case (a, m) if now - m.ts > 1.hour.toMillis => a
      } foreach lastMessageTrackingMap.remove
      maintenanceTs = now
    }
  }
}

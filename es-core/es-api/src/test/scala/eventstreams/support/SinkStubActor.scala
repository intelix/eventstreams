package eventstreams.support

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy}
import eventstreams.{EventFrame, EventAndCursor}
import play.api.libs.json.Json

import scalaz.Scalaz._

trait SinkStubActorSysevents extends BasicSinkStubActorSysevents


object SinkStubActor extends SinkStubActorSysevents {
  def props(requestStrategy: RequestStrategy = WatermarkRequestStrategy(1024, 96)) = Props(new SinkStubActor(requestStrategy))
}

class SinkStubActor(initialStrategyWhenEnabled: RequestStrategy)
  extends BasicSinkStubActor(initialStrategyWhenEnabled) {


  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def handler: Actor.Receive = {
    case OnNext(msg) => msg match {
      case EventAndCursor(value, Some(cursor)) => ReceivedMessageAtSink >>('Contents -> msg, 'Value -> (value ~> 'value | ""), 'Cursor -> cursor)
      case EventAndCursor(value, _) => ReceivedMessageAtSink >>('Contents -> msg, 'Value -> (value ~> 'value | ""), 'Cursor -> "")
      case msg: EventFrame =>
        ReceivedMessageAtSink >> (
          'EventId -> msg.eventIdOrNA,
          'StreamKey -> msg.streamKey,
          'StreamSeed -> msg.streamSeed,
          'Contents -> msg,
          'JsonContents -> Json.stringify(msg.asJson))
      case _ => ReceivedMessageAtSink >> ('Type -> msg.getClass.getSimpleName, 'Contents -> msg)
    }
  }


}
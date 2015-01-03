package eventstreams.support

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor, StoppableSubscriberActor}
import eventstreams.core.agent.core.ProducedMessage

import scalaz.Scalaz._

trait SinkStubActorEvents extends ComponentWithBaseEvents {

  val ReceivedMessageAtSink = 'ReceivedMessageAtSink.info

  override def componentId: String = "Test.SinkStubActor"
}

case class NewRequestStrategy(rs: RequestStrategy)
case class ProduceDemand(i: Int)

object SinkStubActor extends SinkStubActorEvents {
  def props(requestStrategy: RequestStrategy = WatermarkRequestStrategy(1024, 96)) = Props(new SinkStubActor(requestStrategy))
}

class SinkStubActor(initialStrategyWhenEnabled: RequestStrategy)
  extends ActorWithComposableBehavior
  with StoppableSubscriberActor with PipelineWithStatesActor
  with SinkStubActorEvents
  with WithEventPublisher {


  var rsWhenDisabled = ZeroRequestStrategy
  var rsWhenEnabled = initialStrategyWhenEnabled

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) => msg match {
      case ProducedMessage(value, Some(cursor)) => ReceivedMessageAtSink >>('Contents --> msg, 'Value --> (value ~> 'value | ""), 'Cursor --> cursor)
      case ProducedMessage(value, _) => ReceivedMessageAtSink >>('Contents --> msg, 'Value --> (value ~> 'value | ""), 'Cursor --> "")
      case _ => ReceivedMessageAtSink >> ('Contents --> msg)
    }
    case NewRequestStrategy(rs) => rsWhenEnabled = rs
    case ProduceDemand(i) => request(i)

  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => rsWhenEnabled
    case _ => rsWhenDisabled
  }

}
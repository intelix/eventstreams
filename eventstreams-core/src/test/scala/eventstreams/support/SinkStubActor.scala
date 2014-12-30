package eventstreams.support

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor, StoppableSubscriberActor}


trait SinkStubActorEvents extends ComponentWithBaseEvents {

  val ReceivedMessageAtSink = 'ReceivedMessageAtSink.info

  override def componentId: String = "Test.SinkStubActor"
}

object SinkStubActor extends SinkStubActorEvents {
  def props = Props(new SinkStubActor())
}

class SinkStubActor
  extends ActorWithComposableBehavior
  with StoppableSubscriberActor with PipelineWithStatesActor
  with SinkStubActorEvents
  with WithEventPublisher {


  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) =>
      ReceivedMessageAtSink >>('Contents --> msg)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}
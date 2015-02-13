package eventstreams.support

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor, StoppableSubscriberActor}

trait BasicSinkStubActorSysevents extends ComponentWithBaseSysevents {

  val ReceivedMessageAtSink = 'ReceivedMessageAtSink.info

  override def componentId: String = "Test.BasicSinkStubActor"
}

case class NewRequestStrategy(rs: RequestStrategy)
case class ProduceDemand(i: Int)

object BasicSinkStubActor extends SinkStubActorSysevents {
  def props(requestStrategy: RequestStrategy = WatermarkRequestStrategy(1024, 96)) = Props(new BasicSinkStubActor(requestStrategy))
}

class BasicSinkStubActor(initialStrategyWhenEnabled: RequestStrategy)
  extends ActorWithComposableBehavior
  with StoppableSubscriberActor with PipelineWithStatesActor
  with BasicSinkStubActorSysevents
  with WithSyseventPublisher {


  var rsWhenDisabled = ZeroRequestStrategy
  var rsWhenEnabled = initialStrategyWhenEnabled

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) => msg match {
      case _ => ReceivedMessageAtSink >> ('Contents -> msg)
    }
    case NewRequestStrategy(rs) => rsWhenEnabled = rs
    case ProduceDemand(i) => request(i)

  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => rsWhenEnabled
    case _ => rsWhenDisabled
  }

}
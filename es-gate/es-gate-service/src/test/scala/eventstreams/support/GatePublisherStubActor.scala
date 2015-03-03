package eventstreams.support

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.gates.GateState
import eventstreams.{Batch, EventFrame}

import scala.concurrent.duration.FiniteDuration

trait GatePublisherStubSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents with GateMonitorEvents with AtLeastOnceDeliveryActorSysevents {

  val StubFullAcknowledgement = 'StubFullAcknowledgement.info
  val StubConnectedToGate = 'StubConnectedToGate.info

  override def componentId: String = "Test.GatePublisherStub"
}

object GatePublisherStubActor extends GatePublisherStubSysevents {
  def props(address: String): Props = Props(new GatePublisherStubActor(address))

  def id = "gatePublisherStub"
}

class GatePublisherStubActor(address: String)
  extends ActorWithComposableBehavior
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with ActorWithGateStateMonitoring
  with GatePublisherStubSysevents
  with WithSyseventPublisher {

  override def gateStateCheckInterval: FiniteDuration = FiniteDuration(1000, TimeUnit.MILLISECONDS)

  override def connectionEndpoint: Option[String] = Some(address)


  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    initiateReconnect()
  }

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    startGateStateMonitoring()
    StubConnectedToGate >> ('Address -> address)
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    super.onDisconnectedFromEndpoint()
    stopGateStateMonitoring()
    initiateReconnect()
  }

  override def onGateStateChanged(state: GateState): Unit = {
    deliverIfPossible()
    super.onGateStateChanged(state)
  }

  override def canDeliverDownstreamRightNow = connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: Batch[EventFrame]): Unit = {
    StubFullAcknowledgement >> ('CorrelationId -> correlationId)
  }

  private def handler: Receive = {
    case value: EventFrame => deliverMessage(value)
  }


}

package eventstreams.support

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.{TestKit, TestProbe}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor}
import eventstreams.core.agent.core._
import eventstreams.plugins.essentials.GateInstructionConstants._
import play.api.libs.json.Json

import scala.util.Try

private case class Open()

private case class Close()

private case class DoAcknowledgeAsReceived()

private case class DoAcknowledgeAsProcessed()


trait GateStub {
  self: TestKit =>


  def openGate(f: ActorRef) = f ! Open()


  def closeGate(f: ActorRef) = f ! Close()


  def autoAckAsReceivedAtGate(f: ActorRef) = f ! DoAcknowledgeAsReceived()

  def autoAckAsProcessedAtGate(f: ActorRef) = f ! DoAcknowledgeAsProcessed()

  def withGateStub(f: ActorRef => Unit) = {
    val cfg = Json.obj(CfgFAddress -> "/user/testGate")
    val mockGate = system.actorOf(GateStubActor.props, "testGate")
    val probe = TestProbe()
    probe watch mockGate
    try {
      f(mockGate)
    } finally {
      system.stop(mockGate)
      Try { probe.expectTerminated(mockGate) }
    }
  }

}


trait GateStubActorEvents extends ComponentWithBaseEvents {

  val MessageReceivedAtGate = 'MessageReceivedAtGate.info
  val GateStatusCheckReceived = 'GateStatusCheckReceived.info
  val UnrecognisedMessageAtGate = 'UnrecognisedMessageAtGate.info

  override def componentId: String = "Test.GateStubActor"
}


object GateStubActor extends GateStubActorEvents with WithEventPublisher {
  def props = Props(new GateStubActor())
}

class GateStubActor
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with GateStubActorEvents {

  var state: GateState = GateClosed()
  var ackFlags: Set[Any] = Set()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def handler: Receive = {
    case x : DoAcknowledgeAsProcessed => ackFlags = ackFlags + x
    case x : DoAcknowledgeAsReceived => ackFlags = ackFlags + x
    case Open() => state = GateOpen()
    case Close() => state = GateClosed()
    case GateStateCheck(ref) =>
      GateStatusCheckReceived >>()
      ref ! GateStateUpdate(state)
    case Acknowledgeable(msg, id) =>
      MessageReceivedAtGate >>('CorrelationId --> id)
      if (ackFlags.contains(DoAcknowledgeAsReceived())) sender ! AcknowledgeAsReceived(id)
      if (ackFlags.contains(DoAcknowledgeAsProcessed())) sender ! AcknowledgeAsProcessed(id)
    case x => UnrecognisedMessageAtGate >> ('Message --> x)
  }

}

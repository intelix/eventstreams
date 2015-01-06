package eventstreams.support

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.{TestKit, TestProbe}
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.agent.support.ActorSystemWrapper
import eventstreams.core.JsonFrame
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor}
import eventstreams.core.agent.core._
import play.api.libs.json.JsValue

import scala.util.Try

private case class OpenGateStub()

private case class CloseGateStub()

private case class GateStubConfigDoAcknowledgeAsReceived()

private case class GateStubConfigDoAcknowledgeAsProcessed()


trait GateStubTestContext {

  var gates = Map[String, ActorRef]()

  def withGateStub(system: ActorSystemWrapper, name: String) = {
    val actor = system.start(GateStubActor.props, name)
    gates = gates + (name -> actor)
    actor
  }

  def openGate(name: String): Unit = gates.get(name).foreach(openGate)
  def openGate(f: ActorRef): Unit = f ! OpenGateStub()

  def closeGate(name: String): Unit = gates.get(name).foreach(closeGate)
  def closeGate(f: ActorRef): Unit = f ! CloseGateStub()

  def autoAckAsReceivedAtGate(name: String): Unit = gates.get(name).foreach(autoAckAsReceivedAtGate)
  def autoAckAsReceivedAtGate(f: ActorRef): Unit = f ! GateStubConfigDoAcknowledgeAsReceived()

  def autoAckAsProcessedAtGate(name: String): Unit = gates.get(name).foreach(autoAckAsProcessedAtGate)
  def autoAckAsProcessedAtGate(f: ActorRef): Unit = f ! GateStubConfigDoAcknowledgeAsProcessed()


}


trait GateStub {
  self: TestKit =>


  def openGate(f: ActorRef) = f ! OpenGateStub()


  def closeGate(f: ActorRef) = f ! CloseGateStub()


  def autoAckAsReceivedAtGate(f: ActorRef) = f ! GateStubConfigDoAcknowledgeAsReceived()

  def autoAckAsProcessedAtGate(f: ActorRef) = f ! GateStubConfigDoAcknowledgeAsProcessed()

  def withGateStub(f: ActorRef => Unit) = {
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
    case x : GateStubConfigDoAcknowledgeAsProcessed => ackFlags = ackFlags + x
    case x : GateStubConfigDoAcknowledgeAsReceived => ackFlags = ackFlags + x
    case OpenGateStub() => state = GateOpen()
    case CloseGateStub() => state = GateClosed()
    case GateStateCheck(ref) =>
      GateStatusCheckReceived >>()
      ref ! GateStateUpdate(state)
    case Acknowledgeable(msg, id) =>
      val eId = msg match {
        case m: JsonFrame => m.eventIdOrNA
        case m: JsValue => JsonFrame(m, Map()).eventIdOrNA
        case m: ProducedMessage => JsonFrame(m.value, Map()).eventIdOrNA
      }
      MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)
      if (ackFlags.contains(GateStubConfigDoAcknowledgeAsReceived())) sender ! AcknowledgeAsReceived(id)
      if (ackFlags.contains(GateStubConfigDoAcknowledgeAsProcessed())) sender ! AcknowledgeAsProcessed(id)
    case x => UnrecognisedMessageAtGate >> ('Message -> x)
  }

}

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

private case class GateStubAutoCloseAfter(n: Int)


trait GateStubTestContext {

  var gates = Map[String, ActorRef]()

  def withGateStub(system: ActorSystemWrapper, name: String) = {
    val actor = system.start(GateStubActor.props(name), name)
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

  def autoCloseGateAfter(name: String, messagesCount: Int): Unit = gates.get(name).foreach(autoCloseGateAfter(_, messagesCount))
  def autoCloseGateAfter(f: ActorRef, messagesCount: Int): Unit = f ! GateStubAutoCloseAfter(messagesCount)


}


trait GateStub {
  self: TestKit =>


  def openGate(f: ActorRef) = f ! OpenGateStub()


  def closeGate(f: ActorRef) = f ! CloseGateStub()


  def autoAckAsReceivedAtGate(f: ActorRef) = f ! GateStubConfigDoAcknowledgeAsReceived()

  def autoAckAsProcessedAtGate(f: ActorRef) = f ! GateStubConfigDoAcknowledgeAsProcessed()

  def withGateStub(f: ActorRef => Unit) = {
    val mockGate = system.actorOf(GateStubActor.props("testGate"), "testGate")
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
  val OpeningGate = 'OpeningGate.info
  val ClosingGate = 'ClosingGate.info
  val AutoClosingRequested = 'AutoClosingRequested.info
  val AutoClosingGate = 'AutoClosingGate.info

  override def componentId: String = "Test.GateStubActor"
}


object GateStubActor extends GateStubActorEvents with WithEventPublisher {
  def props(name: String) = Props(new GateStubActor(name))
}

class GateStubActor(name: String)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with GateStubActorEvents {

  var state: GateState = GateClosed()
  var ackFlags: Set[Any] = Set()
  var autoCloseCounter: Option[Int] =  None

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('GateName -> name)

  def handler: Receive = {
    case x : GateStubConfigDoAcknowledgeAsProcessed => ackFlags = ackFlags + x
    case x : GateStubConfigDoAcknowledgeAsReceived => ackFlags = ackFlags + x
    case OpenGateStub() =>
      OpeningGate >> ()
      state = GateOpen()
    case CloseGateStub() => 
      ClosingGate >> ()
      state = GateClosed()
    case GateStateCheck(ref) =>
      GateStatusCheckReceived >>('State -> state)
      ref ! GateStateUpdate(state)
    case Acknowledgeable(msg, id) =>
      val eId = msg match {
        case m: JsonFrame => m.eventIdOrNA
        case m: JsValue => JsonFrame(m, Map()).eventIdOrNA
        case m: ProducedMessage => JsonFrame(m.value, Map()).eventIdOrNA
      }
      MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)

      state match {
        case GateClosed() =>
          sender ! GateStateUpdate(state)
        case _ =>
          if (ackFlags.contains(GateStubConfigDoAcknowledgeAsReceived())) sender ! AcknowledgeAsReceived(id)
          if (ackFlags.contains(GateStubConfigDoAcknowledgeAsProcessed())) sender ! AcknowledgeAsProcessed(id)
          autoCloseCounter = autoCloseCounter match {
            case Some(c) if c -1 == 0 =>
              AutoClosingGate >> ()
              state = GateClosed()
              None
            case Some(c) => Some(c-1)
            case None => None
          }
      }
    case GateStubAutoCloseAfter(x) =>
      AutoClosingRequested >> ('Count -> x)
      autoCloseCounter = Some(x)
    case x => UnrecognisedMessageAtGate >> ('Message -> x)
  }

}

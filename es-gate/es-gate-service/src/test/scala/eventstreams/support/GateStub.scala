package eventstreams.support

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.{TestKit, TestProbe}
import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.gates._
import eventstreams._
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor}

import scala.util.Try

private case class OpenGateStub()

private case class CloseGateStub()

private case class GateStubConfigDoAcknowledgeAsReceived()

private case class GateStubConfigDoAcknowledgeAsProcessed()

private case class GateStubAutoCloseAfter(n: Int)

private case class SendToSinks(m: Any)

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

  def sendFromGateToSinks(name: String, m: Any): Unit = gates.get(name).foreach(sendFromGateToSinks(_, m))

  def sendFromGateToSinks(f: ActorRef, m: Any): Unit = f ! SendToSinks(m)

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
      Try {
        probe.expectTerminated(mockGate)
      }
    }
  }

}


trait GateStubActorSysevents extends ComponentWithBaseSysevents {

  val MessageReceivedAtGate = 'MessageReceivedAtGate.info
  val GateStatusCheckReceived = 'GateStatusCheckReceived.info
  val UnrecognisedMessageAtGate = 'UnrecognisedMessageAtGate.info
  val OpeningGate = 'OpeningGate.info
  val ClosingGate = 'ClosingGate.info
  val RegisterSinkReceived = 'RegisterSinkReceived.info
  val AutoClosingRequested = 'AutoClosingRequested.info
  val AutoClosingGate = 'AutoClosingGate.info
  val AcknowledgeAsProcessedReceived = 'AcknowledgeAsProcessed.info
  val AcknowledgeAsReceivedReceived = 'AcknowledgeAsReceivedReceived.info

  override def componentId: String = "Test.GateStubActor"
}


object GateStubActor extends GateStubActorSysevents with WithSyseventPublisher {
  def props(name: String) = Props(new GateStubActor(name))
}

class GateStubActor(name: String)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with GateStubActorSysevents {

  var state: GateState = GateClosed()
  var ackFlags: Set[Any] = Set()
  var autoCloseCounter: Option[Int] = None
  var sinks: Set[ActorRef] = Set()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('GateName -> name)

  def handler: Receive = {
    case x: GateStubConfigDoAcknowledgeAsProcessed => ackFlags = ackFlags + x
    case x: GateStubConfigDoAcknowledgeAsReceived => ackFlags = ackFlags + x
    case OpenGateStub() =>
      OpeningGate >>()
      state = GateOpen()
    case CloseGateStub() =>
      ClosingGate >>()
      state = GateClosed()
    case GateStateCheck(ref) =>
      GateStatusCheckReceived >> ('State -> state)
      ref ! GateStateUpdate(state)
    case Acknowledgeable(msg, id) =>
      val eventsReceived = msg match {
        case m: Batch[_] =>
          m.entries.foreach {
            case x: EventFrame =>
              val eId = x.eventIdOrNA
              MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)
            case x: ProducedMessage =>
              val eId = x.value.eventIdOrNA
              MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)
          }
          m.entries.size
        case m: EventFrame =>
          val eId = m.eventIdOrNA
          MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)
          1
        case m: ProducedMessage =>
          val eId = m.value.eventIdOrNA
          MessageReceivedAtGate >>('CorrelationId -> id, 'EventId -> eId)
          1
      }

      state match {
        case GateClosed() =>
          sender ! GateStateUpdate(state)
        case _ =>
          if (ackFlags.contains(GateStubConfigDoAcknowledgeAsReceived())) sender ! AcknowledgeAsReceived(id)
          if (ackFlags.contains(GateStubConfigDoAcknowledgeAsProcessed())) sender ! AcknowledgeAsProcessed(id)
          autoCloseCounter = autoCloseCounter match {
            case Some(c) if c - eventsReceived <= 0 =>
              AutoClosingGate >>()
              state = GateClosed()
              None
            case Some(c) => Some(c - eventsReceived)
            case None => None
          }
      }
    case GateStubAutoCloseAfter(x) =>
      AutoClosingRequested >> ('Count -> x)
      autoCloseCounter = Some(x)
    case RegisterSink(ref) =>
      RegisterSinkReceived >> ('Ref -> ref)
      sinks += ref
    case SendToSinks(m) => sinks.foreach(_ ! m)
    case AcknowledgeAsProcessed(id) => AcknowledgeAsProcessedReceived >> ('CorrelationId -> id)
    case AcknowledgeAsReceived(id) => AcknowledgeAsReceivedReceived >> ('CorrelationId -> id)
    case x => UnrecognisedMessageAtGate >> ('Message -> x)
  }

}

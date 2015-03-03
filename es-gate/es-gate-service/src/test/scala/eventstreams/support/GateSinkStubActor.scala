package eventstreams.support

import akka.actor.Props
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.gates.RegisterSink
import eventstreams.{AcknowledgeAsProcessed, Acknowledgeable, Batch, EventFrame}

trait GateSinkStubSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents with GateMonitorEvents with AtLeastOnceDeliveryActorSysevents {

  val StubFullAcknowledgement = 'StubFullAcknowledgement.info
  val StubConnectedToGate = 'StubConnectedToGate.info
  val StubSinkReceived = 'StubSinkReceived.info
  val StubSinkReceivedBatch = 'StubSinkReceivedBatch.info

  override def componentId: String = "Test.GateSinkStub"
}

case class DisableAcknowledge()

case class EnableAcknowledge()

object GateSinkStubActor extends GateSinkStubSysevents {
  def props(address: String, id: String): Props = Props(new GateSinkStubActor(address, id))

  def id = "gateSinkStub"
}

class GateSinkStubActor(address: String, id: String)
  extends ActorWithComposableBehavior
  with GateSinkStubSysevents
  with WithSyseventPublisher {

  var ackEnabled = true

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('ID -> id)

  override def preStart(): Unit = {
    super.preStart()
    context.actorSelection(address) ! RegisterSink(self)
  }

  def register(v: EventFrame) = StubSinkReceived >>('EventId -> v.eventIdOrNA, 'StreamKey -> v.streamKey, 'StreamSeed -> v.streamSeed)

  private def handler: Receive = {
    case m: Acknowledgeable[_] =>
      if (ackEnabled) sender ! AcknowledgeAsProcessed(m.id)
      m.msg match {
        case value: EventFrame =>
          register(value)
        case value: Batch[_] =>
          StubSinkReceivedBatch >> ('Size -> value.entries.size)
          value.entries.foreach {
            case v: EventFrame => register(v)
          }
      }
    case DisableAcknowledge() => ackEnabled = false
    case EnableAcknowledge() => ackEnabled = true

  }


}

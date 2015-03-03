package eventstreams

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.flows.internal.{BlackholeAutoAckSinkActor, PassiveInputActor}
import eventstreams.flows.{FlowDeployableSysevents, FlowDeployerActor, FlowManagerActor, FlowProxyActor}
import eventstreams.gates.{GateOpen, GateActor, GateManagerActor}
import eventstreams.instructions.{GateInstructionConstants, EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class GatesTest extends FlatSpec with HubNodeTestContext with WorkerNodeTestContext with SharedActorSystem with LogInstructionConstants with EnrichInstructionConstants {

  trait WithGateManager extends WithHubNode1 with WithWorkerNode1 with GateManagerActorTestContext {

    def validGate1 = Json.obj(
      "name" -> "gate1name",
      "address" -> "gate1",
      "inFlightThreshold" -> 5,
      "noSinkDropMessages" -> false
    )


    startGateManager(hub1System)
    val gateManagerComponentKey = ComponentKey(GateManagerActor.id)
    expectOneOrMoreEvents(MessageRouterActor.RouteAdded, 'Route -> GateManagerActor.id)
  }

  "GateManager" should "start" in new WithHubNode1 with GateManagerActorTestContext {
    startGateManager(hub1System)
    expectOneOrMoreEvents(GateManagerActor.PreStart)
  }

  it should "request all stored gates from storage" in new WithHubNode1 with GateManagerActorTestContext {
    startGateManager(hub1System)
    expectOneOrMoreEvents(ConfigStorageActor.RequestedAllMatchingEntries, 'PartialKey -> (GateManagerActor.id + "/"))
  }

  it should "respond to list subscription with an empty payload" in new WithGateManager {
    startMessageSubscriber1(hub1System)
    subscribeFrom1(hub1System, LocalSubj(gateManagerComponentKey, T_LIST))
    expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "[]")
  }

  it should "respond to configtpl subscription" in new WithGateManager {
    startMessageSubscriber1(hub1System)
    subscribeFrom1(hub1System, LocalSubj(gateManagerComponentKey, T_CONFIGTPL))
    expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "Gate configuration".r)
  }



  it should "create a gate actor in response to add command" in new WithGateManager {

    startMessageSubscriber1(hub1System)
    commandFrom1(hub1System, LocalSubj(gateManagerComponentKey, T_ADD), Some(validGate1))

    expectOneOrMoreEvents(GateActor.PreStart)
    expectOneOrMoreEvents(GateActor.GateConfigured, 'Name -> "gate1name", 'Address -> "gate1")

  }


  trait WithGateCreated extends WithGateManager {
    startMessageSubscriber1(hub1System)
    commandFrom1(hub1System, LocalSubj(gateManagerComponentKey, T_ADD), Some(validGate1))
    expectOneOrMoreEvents(GateActor.GateConfigured, 'Name -> "gate1name", 'Address -> "gate1")
    val gatePublisherRef = startGatePublisherStub("/user/gate1", hub1System)
    val gate1ComponentKey = ComponentKey(locateLastEventFieldValue(GateActor.GateConfigured, "ID").asInstanceOf[String])

    expectExactlyNEvents(1, GatePublisherStubActor.StubConnectedToGate, 'Address -> "/user/gate1")
    clearEvents()
  }

  "Gate" should "open upon activation" in new WithGateCreated {
    commandFrom1(hub1System, LocalSubj(gate1ComponentKey, T_START), None)
    expectExactlyNEvents(1, GateActor.BecomingActive)
    expectOneOrMoreEvents(GatePublisherStubActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")

    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
  }

  trait WithGateOpen extends WithGateCreated {
    commandFrom1(hub1System, LocalSubj(gate1ComponentKey, T_START), None)
    expectExactlyNEvents(1, GateActor.BecomingActive)
    expectOneOrMoreEvents(GatePublisherStubActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")
    clearEvents()
  }

  "Open gate" should "receive message from publisher" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GateActor.NewMessageReceived, 'MessageId -> correlationId)
  }

  it should "should confirm received message as processed" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GatePublisherStubActor.StubFullAcknowledgement, 'CorrelationId -> correlationId)
  }

  it should "schedule message for delivery if noSinkDropMessages is set to false" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
  }

  it should "schedule message for delivery if noSinkDropMessages is set to false - is also config default" in new WithGateOpen {

    override def validGate1 = Json.obj(
      "name" -> "gate1name",
      "address" -> "gate1",
      "inFlightThreshold" -> 5
    )

    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
  }

  it should "drop message if noSinkDropMessages is set to true" in new WithGateOpen {

    override def validGate1 = Json.obj(
      "name" -> "gate1name",
      "address" -> "gate1",
      "inFlightThreshold" -> 5,
      "noSinkDropMessages" -> true
    )

    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GatePublisherStubActor.StubFullAcknowledgement, 'CorrelationId -> correlationId)
    waitAndCheck {
      expectNoEvents(GateActor.ScheduledForDelivery)
    }
  }


}

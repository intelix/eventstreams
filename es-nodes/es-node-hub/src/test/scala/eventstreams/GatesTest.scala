package eventstreams

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.gates.{GateActor, GateManagerActor}
import eventstreams.instructions.{EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class GatesTest extends FlatSpec with HubNodeTestContext with WorkerNodeTestContext with SharedActorSystem with LogInstructionConstants with EnrichInstructionConstants {

  trait WithGateManager extends WithHubNode1 with WithWorkerNode1 with GateManagerActorTestContext {

    def validGate1 = Json.obj(
      "name" -> "gate1name",
      "address" -> "gate1",
      "inFlightThreshold" -> 5,
      "unacknowledgedMessagesResendIntervalSec" -> 1,
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
    val gatePublisherRef = startGatePublisherStub1(hub1System)
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

  it should "accept one sink" in new WithGateCreated {
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateActor.SinkConnected)
  }
  it should "accept second sink" in new WithGateCreated {
    startGateSinkStub1(hub1System)
    startGateSinkStub2(hub1System)
    expectExactlyNEvents(2, GateActor.SinkConnected)
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


  it should "accept one sink" in new WithGateOpen {
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateActor.SinkConnected)
  }

  it should "accept second sink" in new WithGateOpen {
    startGateSinkStub1(hub1System)
    startGateSinkStub2(hub1System)
    expectExactlyNEvents(2, GateActor.SinkConnected)
  }

  it should "deliver scheduled message to the sink once one registers" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
  }

  it should "deliver all scheduled message to the sink once one registers" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(4, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4")
  }

  it should "deliver all scheduled message with the same key and seed in a single batch" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(4, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceivedBatch, 'Size -> 4)
  }

  it should "split messages with different keys into separate batches" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key1").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key1").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(4, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(2, GateSinkStubActor.StubSinkReceivedBatch, 'Size -> 1)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceivedBatch, 'Size -> 2)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4")
  }

  it should "split messages with different seeds into separate batches" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key").setStreamSeed("seed1")
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key").setStreamSeed("seed1")
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(4, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(2, GateSinkStubActor.StubSinkReceivedBatch, 'Size -> 1)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceivedBatch, 'Size -> 2)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4")
  }

  it should "deliver scheduled message to the sink once one registers, second sink arrived after would receive nothing" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    val correlationId = locateLastEventFieldValue(GatePublisherStubActor.ScheduledForDelivery, "CorrelationId")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
    clearEvents()
    startGateSinkStub2(hub1System)
    waitAndCheck {
      expectNoEvents(GateSinkStubActor.StubSinkReceived)
    }
  }

  it should "accept only 5 messages (as per config, delivered with a delay to avoid batching), and then deliver them to sinks once connected. After that accept another message" in new WithGateOpen {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    clearEvents()
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    clearEvents()
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    clearEvents()
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    clearEvents()
    gatePublisherRef ! EventFrame().setEventId("5").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateActor.ScheduledForDelivery)
    clearEvents()
    gatePublisherRef ! EventFrame().setEventId("6").setStreamKey("key").setStreamSeed("seed")
    waitAndCheck {
      expectNoEvents(GateActor.ScheduledForDelivery)
      
    }
    startGateSinkStub1(hub1System)
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "5")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "6")
    gatePublisherRef ! EventFrame().setEventId("7").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "7")
  }

  trait WithTwoSinks extends WithGateOpen {
    startGateSinkStub1(hub1System)
    startGateSinkStub2(hub1System)
    expectExactlyNEvents(2, GateActor.SinkConnected)
    clearEvents()
  }

  "Open gate with two sinks" should "deliver single message to all sinks" in new WithTwoSinks {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1", 'ID -> (GateSinkStubActor.id + "1"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1", 'ID -> (GateSinkStubActor.id + "2"))
  }

  it should "deliver single multiple message to all sinks" in new WithTwoSinks {
    gatePublisherRef ! EventFrame().setEventId("1").setStreamKey("key").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("2").setStreamKey("key1").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("3").setStreamKey("key2").setStreamSeed("seed")
    gatePublisherRef ! EventFrame().setEventId("4").setStreamKey("key").setStreamSeed("seed")
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1", 'ID -> (GateSinkStubActor.id + "1"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "1", 'ID -> (GateSinkStubActor.id + "2"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2", 'ID -> (GateSinkStubActor.id + "1"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "2", 'ID -> (GateSinkStubActor.id + "2"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3", 'ID -> (GateSinkStubActor.id + "1"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "3", 'ID -> (GateSinkStubActor.id + "2"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4", 'ID -> (GateSinkStubActor.id + "1"))
    expectExactlyNEvents(1, GateSinkStubActor.StubSinkReceived, 'EventId -> "4", 'ID -> (GateSinkStubActor.id + "2"))
  }

}

package eventstreams.agent

import akka.actor.ActorSelection
import eventstreams.agent.flow.{DatasourceActor, SubscriberBoundaryInitiatingActor}
import eventstreams.agent.support.AgentControllerSupport
import eventstreams.agent.support.ds.{PublisherStubActor, StubDatasource}
import eventstreams.core.agent.core.CreateDatasource
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.engine.agents.{AgentProxyActor, AgentsManagerActor}
import eventstreams.support.GateStubActor
import org.scalatest.FlatSpec
import play.api.libs.json.{JsValue, Json}

class AgentsTest
  extends FlatSpec with AgentControllerSupport {


  "AgentManager" should "start" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.PreStart)
  }

  it should "receive a handshake from the agent" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.HandshakeReceived)
  }

  it should "create an agent proxy" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.AgentProxyInstanceAvailable)
  }


  "Agent Controller" should "start" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.PreStart)
  }

  it should "attempt connect to the engine" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.AssociationAttempt)
  }

  it should "detect Stub1 as an available datasource" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.AvailableDatasources, 'List -> ("Stub1@" + classOf[StubDatasource].getName))
  }

  it should "connect to the engine" in new WithEngineNode {
    expectSomeEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "send a handshake" in new WithEngineNode {
    val uuid = locateFirstEventFieldValue(AgentControllerActor.AgentInstanceAvailable, "Id")
    expectSomeEvents(AgentsManagerActor.HandshakeReceived, 'AgentID -> uuid)
  }

  it should "reconnect to the engine if disconnected" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.HandshakeReceived)
    clearEvents()
    restartEngineNode()
    expectSomeEvents(AgentControllerActor.AssociationAttempt)
    expectSomeEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "not create a datasource instance yet" in new WithEngineNode {
    waitAndCheck {
      expectNoEvents(AgentControllerActor.DatasourceInstanceAvailable)
    }
  }

  it should "create a datasource actor on demand" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj()))
    expectSomeEvents(1, AgentControllerActor.DatasourceInstanceAvailable)
  }


  "Agent Controller with datasource actor" should "not have datasource instance if config is blank" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj()))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if class is missing" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj(), "targetGate" -> "akka.tcp://engine@localhost:12554/user/gate1")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if class is wrong" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "xx"), "targetGate" -> "akka.tcp://engine@localhost:12554/user/gate1")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if targetGate is missing" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"))))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if targetGate is blank" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "have datasource instance if config is valid" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12554/user/gate1")))
    expectSomeEvents(1, DatasourceActor.DatasourceReady)
  }


  "Datasource Proxy" should "start when datasource is created" in new WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12554/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
  }

  trait WithDatasourceStarted extends WithEngineNode {
    sendToAgentController(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12554/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    val datasourceProxyRoute = locateLastEventFieldValue(MessageRouterActor.RouteAdded, "Route").asInstanceOf[String]
    val datasourcePublisherActorRef = withSystem[ActorSelection](AgentSystem) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    clearEvents()

    def publishEventFromDatasource(j: JsValue) = datasourcePublisherActorRef ! j
  }

  trait WithDatasourceActivated extends WithDatasourceStarted {
    sendCommand(datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(1, DatasourceActor.BecomingActive)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()
  }

  trait WithDatasourceActivatedAndGateCreated extends WithDatasourceActivated {
    startGate("gate1")
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociatedWithRemoteActor)
    expectSomeEvents(1, GateStubActor.GateStatusCheckReceived)
    clearEvents()
  }

  it should "activate datasource on command" in new WithDatasourceActivated {
  }

  "when datasource is started, Datasource" should "attempt connecting to the gate when datasource is started" in new WithDatasourceStarted {
    sendCommand(datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
  }

  it should "connect to the gate if gate is available and start gate monitoring" in new WithDatasourceActivatedAndGateCreated {
  }


  it should "not produce any events at the gate yet" in new WithDatasourceActivatedAndGateCreated {
    waitAndCheck {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
    }
  }

  it should "detect when gate opens" in new WithDatasourceActivatedAndGateCreated {
    openGate("gate1")
    expectSomeEvents(SubscriberBoundaryInitiatingActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published at the same time with gate opening" in new WithDatasourceActivatedAndGateCreated {
    openGate("gate1")
    publishEventFromDatasource(Json.obj("eventId" -> "1"))
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published after gate opening" in new WithDatasourceActivatedAndGateCreated {
    openGate("gate1")
    expectSomeEvents(SubscriberBoundaryInitiatingActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
    }
    publishEventFromDatasource(Json.obj("eventId" -> "1"))
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published before gate opening" in new WithDatasourceActivatedAndGateCreated {
    publishEventFromDatasource(Json.obj("eventId" -> "1"))
    expectSomeEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
      expectNoEvents(SubscriberBoundaryInitiatingActor.DeliveringToActor)
    }
    clearEvents()
    openGate("gate1")
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }

  trait WithThreeEventsAvailAndOpenNotAckingGate extends WithDatasourceActivatedAndGateCreated{
    publishEventFromDatasource(Json.obj("eventId" -> "1"))
    expectSomeEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    clearEvents()
    publishEventFromDatasource(Json.obj("eventId" -> "2"))
    openGate("gate1")
    publishEventFromDatasource(Json.obj("eventId" -> "3"))
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.MessageReceivedAtGate)
    }
    expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
  }

  "when datasource is up gate is open and 3 events available, Datasource" should "produce a single event at the gate if gate not acking" in new WithThreeEventsAvailAndOpenNotAckingGate {
  }

  it should "attempt redelivering that single message" in new WithThreeEventsAvailAndOpenNotAckingGate {
    expectSomeEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 0)
    expectSomeEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 1)
    expectSomeEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 2)
    expectSomeEvents(3, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "2")
  }

  it should "not attempt redelivery if gate acked as received" in new WithThreeEventsAvailAndOpenNotAckingGate {
    autoAckAsReceivedAtGate("gate1")
    duringPeriodInMillis(3000) {
      expectNoEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 2)
    }
  }

  it should "deliver all messages if gate is acking as processed" in new WithThreeEventsAvailAndOpenNotAckingGate {
    autoAckAsReceivedAtGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "2")
    expectSomeEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "3")
  }



}

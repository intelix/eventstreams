package eventstreams.agent

import eventstreams.agent.flow.{DatasourceActor, SubscriberBoundaryInitiatingActor}
import eventstreams.agent.support.AgentControllerSupport
import eventstreams.agent.support.ds.StubDatasource
import eventstreams.core.agent.core.CreateDatasource
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.engine.agents.{AgentProxyActor, AgentsManagerActor}
import eventstreams.support.{GateStubActor, PublisherStubActor}
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class AgentsTest
  extends FlatSpec with AgentControllerSupport  {


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
    clearEvents()
  }

  it should "activate datasource on command" in new WithDatasourceStarted {
    sendCommand(datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, DatasourceActor.BecomingActive)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)
  }

  "when datasource is started, Datasource sink" should "attempt connecting to the gate when datasource is started" in new WithDatasourceStarted {
    sendCommand(datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
  }

  it should "connect to the gate if gate is available and start gate monitoring" in new WithDatasourceStarted {
    sendCommand(datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    startGate("gate1")
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociatedWithRemoteActor)
    expectSomeEvents(1, GateStubActor.GateStatusCheckReceived)
  }



}

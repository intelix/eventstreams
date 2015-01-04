package eventstreams.agent

import core.events.EventOps.symbolToEventField
import eventstreams.agent.flow.DatasourceActor
import eventstreams.agent.support.AgentControllerSupport
import eventstreams.agent.support.ds.StubDatasource
import eventstreams.core.agent.core.CreateDatasource
import eventstreams.engine.agents.AgentsManagerActor
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class AgentControllerTest
  extends FlatSpec with AgentControllerSupport  {


  "AgentController" should "start" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.PreStart)
  }

  it should "attempt connect to the engine" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.AssociationAttempt)
  }

  it should "detect Stub1 as an available datasource" in new DefaultContext {
    expectSomeEvents(AgentControllerActor.AvailableDatasources, 'List --> ("Stub1@" + classOf[StubDatasource].getName))
  }

  it should "connect to the engine" in new WithEngineNode {
    expectSomeEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "send a handshake" in new WithEngineNode {
    val uuid = locateFirstEventFieldValue(AgentControllerActor.AgentInstanceAvailable, "Id")
    expectSomeEvents(AgentsManagerActor.HandshakeReceived, 'AgentID --> uuid)
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


  "AgentController with datasource actor" should "not have datasource instance if config is blank" in new WithEngineNode {
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





}

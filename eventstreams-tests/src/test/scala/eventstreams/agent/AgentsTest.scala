package eventstreams.agent

import akka.actor.ActorSelection
import eventstreams.agent.datasource.{DatasourceActor, SubscriberBoundaryInitiatingActor}
import eventstreams.core.Tools.configHelper
import eventstreams.core.agent.core.CreateDatasource
import eventstreams.core.messages.{ComponentKey, LocalSubj}
import eventstreams.engine.agents.{AgentProxyActor, AgentsManagerActor, DatasourceProxyActor}
import eventstreams.support.ds.{PublisherStubActor, StubDatasource}
import eventstreams.support.{IsolatedActorSystems, GateStubActor, MultiNodeTestingSupport}
import org.scalatest.FlatSpec
import play.api.libs.json.{JsArray, JsValue, Json}

class AgentsTest
  extends FlatSpec with MultiNodeTestingSupport with IsolatedActorSystems {


  "AgentManager" should "start" in new WithAgentNode1 with WithEngineNode1  {
    expectSomeEvents(AgentsManagerActor.PreStart)
  }

  it should "receive a handshake from the agent" in new WithAgentNode1 with WithEngineNode1  {
    expectSomeEvents(AgentsManagerActor.HandshakeReceived)
  }

  it should "create an agent proxy" in new WithAgentNode1 with WithEngineNode1  {
    expectSomeEvents(AgentsManagerActor.AgentProxyInstanceAvailable)
  }

  trait WithSubscriberForAgentManager extends WithAgentNode1 with WithEngineNode1  {
    expectSomeEvents(AgentsManagerActor.PreStart)
    val agentManagerRoute = ComponentKey(locateLastEventFieldValue(AgentsManagerActor.PreStart, "ComponentKey").asInstanceOf[String])
    withSystem(EngineSystemPrefix, 1) { sys =>
      startMessageSubscriber1(sys)
      subscribeFrom1(sys, LocalSubj(agentManagerRoute, T_LIST))
    }
  }

  it should "accept a subscriber for the route" in new WithSubscriberForAgentManager {
    expectSomeEvents(AgentsManagerActor.NewSubjectSubscription)
    expectSomeEvents(AgentsManagerActor.FirstSubjectSubscriber)
  }

  it should "respond to the list subscriber" in new WithSubscriberForAgentManager {
    expectSomeEvents(AgentsManagerActor.UpdateForSubject)
    val data = Json.parse(locateFirstEventFieldValue(AgentsManagerActor.UpdateForSubject, "Data").asInstanceOf[String])
    data.as[JsArray].value should be(empty)
  }


  "Agent Controller" should "start" in new WithAgentNode1 {
    expectSomeEvents(AgentControllerActor.PreStart)
  }

  it should "attempt connect to the engine" in new WithAgentNode1 {
    expectSomeEvents(AgentControllerActor.AssociationAttempt)
  }

  it should "detect Stub1 as an available datasource" in new WithAgentNode1 {
    expectSomeEvents(AgentControllerActor.AvailableDatasources, 'List -> ("Stub1@" + classOf[StubDatasource].getName))
  }

  it should "connect to the engine" in new WithAgentNode1 with WithEngineNode1 {
    expectSomeEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "send a handshake" in new WithAgentNode1 with WithEngineNode1  {
    val uuid = locateFirstEventFieldValue(AgentControllerActor.AgentInstanceAvailable, "Id")
    expectSomeEvents(AgentsManagerActor.HandshakeReceived, 'AgentID -> uuid)
  }

  it should "reconnect to the engine if disconnected" in new WithAgentNode1 with WithEngineNode1  {
    expectSomeEvents(AgentsManagerActor.HandshakeReceived)
    clearEvents()
    restartEngineNode1()
    expectSomeEvents(AgentControllerActor.AssociationAttempt)
    expectSomeEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "not create a datasource instance yet" in new WithAgentNode1 with WithEngineNode1  {
    waitAndCheck {
      expectNoEvents(AgentControllerActor.DatasourceInstanceAvailable)
    }
  }

  it should "create a datasource actor on demand" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj()))
    expectSomeEvents(1, AgentControllerActor.DatasourceInstanceAvailable)
  }

  "AgentManager with a subscriber" should "update a subscirber when new agent arrives" in new WithSubscriberForAgentManager {
    expectSomeEvents(AgentsManagerActor.UpdateForSubject)
    clearEvents()
    sendToAgentController1(CreateDatasource(Json.obj()))
    expectSomeEvents(AgentsManagerActor.UpdateForSubject)
    val data = Json.parse(locateFirstEventFieldValue(AgentsManagerActor.UpdateForSubject, "Data").asInstanceOf[String])
    data.as[JsArray].value should not be empty

  }

  "Agent Controller with datasource actor" should "not have datasource instance if config is blank" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj()))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if class is missing" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj(), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if class is wrong" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "xx"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if targetGate is missing" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"))))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "not have datasource instance if targetGate is blank" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "")))
    waitAndCheck {
      expectNoEvents(DatasourceActor.DatasourceReady)
    }
  }

  it should "have datasource instance if config is valid" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, DatasourceActor.DatasourceReady)
  }


  "Datasource Proxy" should "start when datasource is created" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
  }

  "Datasource" should "communicate the current state (passive)" in new WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(DatasourceProxyActor.InfoUpdate)
    val infoUpdate = locateFirstEventFieldValue(DatasourceProxyActor.InfoUpdate, "Data").asInstanceOf[String]
    Json.parse(infoUpdate) ~> 'state should be(Some("passive"))
  }


  trait WithDatasourceStarted extends WithAgentNode1 with WithEngineNode1  {
    sendToAgentController1(CreateDatasource(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    expectSomeEvents(DatasourceProxyActor.PreStart)
    expectSomeEvents(PublisherStubActor.PreStart)
    val datasourceProxyRoute = locateLastEventFieldValue(DatasourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    val datasourcePublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    clearEvents()

    def publishEventFromDatasource(j: JsValue) = datasourcePublisherActorRef ! j
  }

  trait WithDatasourceActivated extends WithDatasourceStarted {
    sendCommand(engine1System, datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(1, DatasourceActor.BecomingActive)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()
  }

  trait WithDatasourceActivatedAndGateCreated extends WithDatasourceActivated {
    startGate1("gate1")
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociatedWithRemoteActor)
    expectSomeEvents(1, GateStubActor.GateStatusCheckReceived)
    clearEvents()
  }

  it should "activate datasource on command" in new WithDatasourceActivated {
  }

  "when datasource is started, Datasource" should "attempt connecting to the gate when datasource is started" in new WithDatasourceStarted {
    sendCommand(engine1System, datasourceProxyRoute, T_START, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
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


  trait WithThreeEventsAvailAndOpenNotAckingGate extends WithDatasourceActivatedAndGateCreated {
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


  trait WithOneEventsAvailAndClosedGate extends WithDatasourceActivatedAndGateCreated {
    publishEventFromDatasource(Json.obj("eventId" -> "1"))
    expectSomeEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    clearEvents()
  }

  "when datasource is up gate is closed and 1 event available, Datasource" should "reconnect to the gate if connection drops" in new WithOneEventsAvailAndClosedGate {
    restartEngineNode1()
    startGate1("gate1")
    expectSomeEvents(AgentProxyActor.DatasourceProxyUp)
    expectSomeEvents(GateStubActor.GateStatusCheckReceived)
    clearEvents()
    openGate("gate1")
    expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
  }

  it should "communicate the current state once reconnected" in new WithOneEventsAvailAndClosedGate {
    clearEvents()
    restartEngineNode1()
    startGate1("gate1")
    expectSomeEvents(AgentProxyActor.DatasourceProxyUp)
    expectSomeEvents(GateStubActor.GateStatusCheckReceived)
    openGate("gate1")
    expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectSomeEvents(DatasourceProxyActor.InfoUpdate)
    val infoUpdate = locateFirstEventFieldValue(DatasourceProxyActor.InfoUpdate, "Data").asInstanceOf[String]
    Json.parse(infoUpdate) ~> 'state should be(Some("active"))
  }

  trait With100Events extends WithDatasourceActivatedAndGateCreated {
    (1 to 100).foreach { i =>
      publishEventFromDatasource(Json.obj("eventId" -> i.toString))
    }

  }

  "when datasource is up and 100 events available, Datasource" should "deliver all messages when gate is immediatelly up" in new With100Events {
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")

    (1 to 100).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "deliver all messages when gate is eventually up - with a delay" in new With100Events {
    Thread.sleep(3000)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 100).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }
  it should "deliver all messages even if engine restarts in between" taggedAs OnlyThisTest in new With100Events {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartEngineNode1()
    startGate1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (26 to 100).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "deliver all messages even if engine restarts multiple times in between" taggedAs OnlyThisTest in new With100Events {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartEngineNode1()
    autoCloseGateAfter("gate1", 25)
    startGate1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (26 to 50).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartEngineNode1()
    startGate1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (51 to 100).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "stop delivering messages as soon as gate closes" in new With100Events {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "99")
    }
  }

  it should "stop delivering messages as soon as gate closes - and resume delivery as soon as gate opens again" in new With100Events {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "99")
    }
    openGate("gate1")
    (1 to 100).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  "AgentProxy" should "create datasource on command" in new WithAgentNode1 with WithEngineNode1  {
    implicit val sys = engine1System
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
  }

  it should "create be able to create multiple datasources" in new WithAgentNode1 with WithEngineNode1  {
    implicit val sys = engine1System
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    val dsProxy1Route = locateFirstEventFieldValue(DatasourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    clearEvents()
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate2")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    val dsProxy2Route = locateFirstEventFieldValue(DatasourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    dsProxy1Route should not be dsProxy2Route
  }


  trait WithSubscriberForAgentProxy extends WithAgentNode1 with WithEngineNode1  {
    implicit val sys = engine1System
    val agentProxyRoute = ComponentKey(locateLastEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String])
    withSystem(EngineSystemPrefix, 1) { sys =>
      startMessageSubscriber1(sys)
    }
  }

  it should "accept a subscriber for the list" in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_LIST))
    expectSomeEvents(AgentProxyActor.NewSubjectSubscription)
    expectSomeEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the list subscriber"  in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_LIST))
    expectSomeEvents(AgentProxyActor.UpdateForSubject)
    val data = Json.parse(locateFirstEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String])
    data.as[JsArray].value should be(empty)
  }


  it should "send updates to the list subscriber when datasources are created"  in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_LIST))
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    expectSomeEvents(AgentProxyActor.UpdateForSubject)
    clearEvents()
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    waitAndCheck {
      expectSomeEvents(AgentProxyActor.UpdateForSubject)
    }
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]).as[JsArray].value should have size 1
    clearEvents()
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate2")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    waitAndCheck {
      expectSomeEvents(AgentProxyActor.UpdateForSubject)
    }
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]).as[JsArray].value should have size 2
  }


  it should "accept a subscriber for the info" in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_INFO))
    expectSomeEvents(AgentProxyActor.NewSubjectSubscription)
    expectSomeEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the info subscriber"  in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_INFO))
    expectSomeEvents(AgentProxyActor.UpdateForSubject)
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]) ~> 'name should be (Some("agent1"))
  }

  it should "accept a subscriber for the configtpl" in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_CONFIGTPL))
    expectSomeEvents(AgentProxyActor.NewSubjectSubscription)
    expectSomeEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the configtpl subscriber" in new WithSubscriberForAgentProxy {
    subscribeFrom1(engine1System, LocalSubj(agentProxyRoute, T_CONFIGTPL))
    expectSomeEvents(AgentProxyActor.UpdateForSubject)
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]) ~> "title" should not be None
  }



  trait WithTwoDatasources extends WithAgentNode1 with WithEngineNode1  {
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate1")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    val dsProxy1Route = locateFirstEventFieldValue(DatasourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    var ds1PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    var ds1ComponentKey = locateFirstEventFieldValue(DatasourceActor.PreStart, "ComponentKey").asInstanceOf[String]

    clearEvents()
    sendCommand(engine1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate2")))
    expectSomeEvents(1, AgentProxyActor.DatasourceProxyUp)
    val dsProxy2Route = locateFirstEventFieldValue(DatasourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    var ds2PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    var ds2ComponentKey = locateFirstEventFieldValue(DatasourceActor.PreStart, "ComponentKey").asInstanceOf[String]
    dsProxy1Route should not be dsProxy2Route

    startGate1("gate1")
    startGate1("gate2")
    autoAckAsProcessedAtGate("gate1")
    autoAckAsProcessedAtGate("gate2")
    openGate("gate1")
    openGate("gate2")

    clearEvents()

    def publishEventFromDatasource1(j: JsValue) = ds1PublisherActorRef ! j

    def publishEventFromDatasource2(j: JsValue) = ds2PublisherActorRef ! j
  }

  "when two datasources created, and both gates available, AgentProxy" should "be able to activate one" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
    }

    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(1, DatasourceActor.BecomingActive)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)

  }

  it should "be able to activate another" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(1, DatasourceActor.BecomingActive)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)

  }
  it should "be able to activate both" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, DatasourceActor.BecomingActive)
    expectSomeEvents(2, PublisherStubActor.BecomingActive)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)

  }

  it should "be able to activate both and then stop one" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, DatasourceActor.BecomingActive)
    expectSomeEvents(2, PublisherStubActor.BecomingActive)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(engine1System, dsProxy1Route, T_STOP, None)
    expectSomeEvents(SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)
    expectSomeEvents(1, DatasourceActor.BecomingPassive, 'ComponentKey -> ds1ComponentKey)
    waitAndCheck {
      expectNoEvents(DatasourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }

  }

  it should "be able to activate both and then kill one" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, DatasourceActor.BecomingActive)
    expectSomeEvents(2, PublisherStubActor.BecomingActive)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(engine1System, dsProxy1Route, T_KILL, None)
    expectSomeEvents(1, DatasourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectSomeEvents(1, DatasourceProxyActor.PostStop)
    waitAndCheck {
      expectNoEvents(DatasourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }
    sendCommand(engine1System, dsProxy2Route, T_STOP, None)
    expectSomeEvents(SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)

  }

  it should "be able to activate both and then kill both" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, DatasourceActor.BecomingActive)
    expectSomeEvents(2, PublisherStubActor.BecomingActive)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(engine1System, dsProxy1Route, T_KILL, None)
    sendCommand(engine1System, dsProxy2Route, T_KILL, None)
    expectSomeEvents(1, DatasourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectSomeEvents(1, DatasourceActor.PostStop, 'ComponentKey -> ds2ComponentKey)
    expectSomeEvents(2, DatasourceProxyActor.PostStop)

  }

  it should "be able to kill both" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_KILL, None)
    sendCommand(engine1System, dsProxy2Route, T_KILL, None)
    expectSomeEvents(1, DatasourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectSomeEvents(1, DatasourceActor.PostStop, 'ComponentKey -> ds2ComponentKey)
    expectSomeEvents(2, DatasourceProxyActor.PostStop)

  }

  it should "be able to activate both and then stop both" in new WithTwoDatasources {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectSomeEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectSomeEvents(2, DatasourceActor.BecomingActive)
    expectSomeEvents(2, PublisherStubActor.BecomingActive)
    expectSomeEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(engine1System, dsProxy1Route, T_STOP, None)
    sendCommand(engine1System, dsProxy2Route, T_STOP, None)
    expectSomeEvents(2,SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)
    expectSomeEvents(1, DatasourceActor.BecomingPassive, 'ComponentKey -> ds1ComponentKey)
    expectSomeEvents(1, DatasourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)

  }


  it should "terminate when agent terminates" in new WithTwoDatasources {
    restartAgentNode1()
    expectSomeEvents(2, DatasourceProxyActor.PostStop)
    expectSomeEvents(1, AgentProxyActor.PostStop)

  }

  it should "recreate actor hierarchy when agent reconnects" in new WithTwoDatasources {
    restartAgentNode1()
    expectSomeEvents(2, DatasourceProxyActor.PostStop)
    expectSomeEvents(1, AgentProxyActor.PostStop)
    expectSomeEvents(AgentProxyActor.PreStart)
  }





  trait WithTwoDatasourcesAnd10EventsForEach extends WithTwoDatasources {
    (1 to 10) foreach { i =>
      val v = Json.obj("eventId" -> i.toString)
      publishEventFromDatasource1(v)
      publishEventFromDatasource2(v)
    }
  }




  "when two datasources created, both gates available, and 10 events available for publishing, Datasource" should
    "publish 10 messages to gate1 when first activated, gate2 sould receive nothing"  in new WithTwoDatasourcesAnd10EventsForEach {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    (1 to 10).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
    }
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'GateName -> "gate2")

  }

  it should "publish 10 messages to gate2 when second activated, gate1 sould receive nothing" in new WithTwoDatasourcesAnd10EventsForEach {
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    (1 to 10).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'GateName -> "gate1")

  }

  it should "publish 10 messages each to gate1 and gate2 when both activated"  in new WithTwoDatasourcesAnd10EventsForEach {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)
    (1 to 10).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }

  }


  "when two datasources created, both gates available, and 10 events available for publishing, AgentProxy" should
    "be able to activate both and then reset one once all messages are published" in new WithTwoDatasourcesAnd10EventsForEach {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)

    (1 to 10).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    clearEvents()

    sendCommand(engine1System, dsProxy1Route, T_RESET, None)
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.PostStop)
    expectSomeEvents(1, PublisherStubActor.PostStop)
    expectSomeEvents(1, PublisherStubActor.PublisherStubStarted, 'InitialState -> "None")
    expectSomeEvents(1, PublisherStubActor.BecomingActive)

    waitAndCheck {
      expectNoEvents(DatasourceProxyActor.PostStop)
      expectNoEvents(DatasourceActor.PostStop)
      expectNoEvents(DatasourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }

  }

  trait WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 extends WithTwoDatasourcesAnd10EventsForEach {
    sendCommand(engine1System, dsProxy1Route, T_START, None)
    sendCommand(engine1System, dsProxy2Route, T_START, None)

    clearEvents()


    (1 to 10).foreach { i =>
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    clearEvents()

    startGate1("gate3")
    autoAckAsProcessedAtGate("gate3")
    openGate("gate3")


    sendCommand(engine1System, dsProxy1Route, T_UPDATE_PROPS, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://engine@localhost:12521/user/gate3")))
    expectSomeEvents(1, SubscriberBoundaryInitiatingActor.PostStop)
    expectSomeEvents(1, PublisherStubActor.PostStop)
    expectSomeEvents(1, PublisherStubActor.BecomingActive)
    // the ref will change...
    ds1PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }

    waitAndCheck {
      expectNoEvents(DatasourceProxyActor.PostStop)
      expectNoEvents(DatasourceActor.PostStop)
      expectNoEvents(DatasourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }
  }

  "when 2 ds activated, 10 events published from each, 1st datasource reconfigured to point to gate3" should
    "restart publisher previous state as an initial state" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    expectSomeEvents(1, PublisherStubActor.PublisherStubStarted, 'InitialState -> "Some({\"id\":\"10\"})")
  }

  it should "publish to gate3 any new messages" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    clearEvents()
    publishEventFromDatasource1(Json.obj("eventId" -> "abc"))
    expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate3")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate1")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate2")

  }

  it should "2nd ds still publish to gate2" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    clearEvents()
    publishEventFromDatasource2(Json.obj("eventId" -> "abc"))
    expectSomeEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate2")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate1")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate3")

  }




}

package eventstreams

import akka.actor.ActorSelection
import eventstreams.Tools.configHelper
import eventstreams.agent._
import eventstreams.support.{EventsourceStub, _}
import org.scalatest.FlatSpec
import play.api.libs.json.{JsArray, Json}

class AgentsTest
  extends FlatSpec with AgentNodeTestContext with HubNodeTestContext with IsolatedActorSystems {


  "AgentManager" should "start" in new WithAgentNode1 with WithHubNode1  {
    expectOneOrMoreEvents(AgentsManagerActor.PreStart)
  }

  it should "receive a handshake from the agent" in new WithAgentNode1 with WithHubNode1  {
    expectOneOrMoreEvents(AgentsManagerActor.HandshakeReceived)
  }

  it should "create an agent proxy" in new WithAgentNode1 with WithHubNode1  {
    expectOneOrMoreEvents(AgentsManagerActor.AgentProxyInstanceAvailable)
  }

  trait WithSubscriberForAgentManager extends WithAgentNode1 with WithHubNode1  {
    expectOneOrMoreEvents(AgentsManagerActor.PreStart)
    val agentManagerRoute = ComponentKey(locateLastEventFieldValue(AgentsManagerActor.PreStart, "ComponentKey").asInstanceOf[String])
    withSystem(HubSystemPrefix, 1) { sys =>
      startMessageSubscriber1(sys)
      subscribeFrom1(sys, LocalSubj(agentManagerRoute, T_LIST))
    }
  }

  it should "accept a subscriber for the route" in new WithSubscriberForAgentManager {
    expectOneOrMoreEvents(AgentsManagerActor.NewSubjectSubscription)
    expectOneOrMoreEvents(AgentsManagerActor.FirstSubjectSubscriber)
  }

  it should "respond to the list subscriber" in new WithSubscriberForAgentManager {
    expectOneOrMoreEvents(AgentsManagerActor.UpdateForSubject)
    val data = Json.parse(locateFirstEventFieldValue(AgentsManagerActor.UpdateForSubject, "Data").asInstanceOf[String])
    data.as[JsArray].value should be(empty)
  }


  "Agent Controller" should "start" in new WithAgentNode1 {
    expectOneOrMoreEvents(AgentControllerActor.PreStart)
  }

  it should "attempt connect to the hub" in new WithAgentNode1 {
    expectOneOrMoreEvents(AgentControllerActor.AssociationAttempt)
  }

  it should "detect Stub1 as an available eventsource" in new WithAgentNode1 {
    expectOneOrMoreEvents(AgentControllerActor.AvailableEventsources, 'List -> ("Stub1@" + classOf[EventsourceStub].getName))
  }

  it should "connect to the hub" in new WithAgentNode1 with WithHubNode1 {
    expectOneOrMoreEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "send a handshake" in new WithAgentNode1 with WithHubNode1  {
    val uuid = locateFirstEventFieldValue(AgentControllerActor.AgentInstanceAvailable, "Id")
    expectOneOrMoreEvents(AgentsManagerActor.HandshakeReceived, 'AgentID -> uuid)
  }

  it should "reconnect to the hub if disconnected" in new WithAgentNode1 with WithHubNode1  {
    expectOneOrMoreEvents(AgentsManagerActor.HandshakeReceived)
    clearEvents()
    restartHubNode1()
    expectOneOrMoreEvents(AgentControllerActor.AssociationAttempt)
    expectOneOrMoreEvents(AgentControllerActor.AssociatedWithRemoteActor)
  }

  it should "not create a eventsource instance yet" in new WithAgentNode1 with WithHubNode1  {
    waitAndCheck {
      expectNoEvents(AgentControllerActor.EntityInstanceAvailable)
    }
  }

  it should "create a eventsource actor on demand" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj())))
    expectExactlyNEvents(1, AgentControllerActor.EntityInstanceAvailable)
  }

  "AgentManager with a subscriber" should "update a subscriber when new agent arrives" in new WithSubscriberForAgentManager {
    expectOneOrMoreEvents(AgentsManagerActor.UpdateForSubject)
  }

  "Agent Controller with eventsource actor" should "not have eventsource instance if config is blank" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj())))
    waitAndCheck {
      expectNoEvents(EventsourceActor.EventsourceReady)
    }
  }

  it should "not have eventsource instance if class is missing" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj(), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1"))))
    waitAndCheck {
      expectNoEvents(EventsourceActor.EventsourceReady)
    }
  }

  it should "not have eventsource instance if class is wrong" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "xx"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1"))))
    waitAndCheck {
      expectNoEvents(EventsourceActor.EventsourceReady)
    }
  }

  it should "not have eventsource instance if targetGate is missing" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "stub")))))
    waitAndCheck {
      expectNoEvents(EventsourceActor.EventsourceReady)
    }
  }

  it should "not have eventsource instance if targetGate is blank" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> ""))))
    waitAndCheck {
      expectNoEvents(EventsourceActor.EventsourceReady)
    }
  }

  it should "have eventsource instance if config is valid" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1"))))
    expectExactlyNEvents(1, EventsourceActor.EventsourceReady)
  }


  "Eventsource Proxy" should "start when eventsource is created" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1"))))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
  }

  "Eventsource" should "communicate the current state (passive)" in new WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1"))))
    expectOneOrMoreEvents(EventsourceProxyActor.InfoUpdate)
    val infoUpdate = locateFirstEventFieldValue(EventsourceProxyActor.InfoUpdate, "Data").asInstanceOf[String]
    Json.parse(infoUpdate) ~> 'state should be(Some("passive"))
  }


  trait WithEventsourceStarted extends WithAgentNode1 with WithHubNode1  {
    sendToAgentController1(CreateEventsource(Json.stringify(Json.obj(
      "source" -> Json.obj("class" -> "stub"),
      "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1",
      "maxBatchSize" -> 5
    ))))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    expectOneOrMoreEvents(EventsourceProxyActor.PreStart)
    expectOneOrMoreEvents(PublisherStubActor.PreStart)
    val eventsourceProxyRoute = locateLastEventFieldValue(EventsourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    val eventsourcePublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    clearEvents()

    def publishEventFromEventsource(j: EventFrame) = eventsourcePublisherActorRef ! j
  }

  trait WithEventsourceActivated extends WithEventsourceStarted {
    sendCommand(hub1System, eventsourceProxyRoute, T_START, None)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(1, EventsourceActor.BecomingActive)
    expectExactlyNEvents(1, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()
  }

  trait WithEventsourceActivatedAndGateCreated extends WithEventsourceActivated {
    startGateStub1("gate1")
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.AssociatedWithRemoteActor)
    expectOneOrMoreEvents(GateStubActor.GateStatusCheckReceived)
    clearEvents()
  }

  it should "activate eventsource on command" in new WithEventsourceActivated {
  }

  "when eventsource is started, Eventsource" should "attempt connecting to the gate when eventsource is started" in new WithEventsourceStarted {
    sendCommand(hub1System, eventsourceProxyRoute, T_START, None)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
  }


  it should "not produce any events at the gate yet" in new WithEventsourceActivatedAndGateCreated {
    waitAndCheck {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
    }
  }

  it should "detect when gate opens" in new WithEventsourceActivatedAndGateCreated {
    openGate("gate1")
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published at the same time with gate opening" in new WithEventsourceActivatedAndGateCreated {
    openGate("gate1")
    publishEventFromEventsource(EventFrame("eventId" -> "1"))
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published after gate opening" in new WithEventsourceActivatedAndGateCreated {
    openGate("gate1")
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.MonitoredGateStateChanged, 'NewState -> "GateOpen()")
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
    }
    publishEventFromEventsource(EventFrame("eventId" -> "1"))
    waitAndCheck {
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }

  it should "produce a single event at the gate if there is a demand and gate is open and event is published before gate opening" in new WithEventsourceActivatedAndGateCreated {
    publishEventFromEventsource(EventFrame("eventId" -> "1"))
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate)
      expectNoEvents(SubscriberBoundaryInitiatingActor.DeliveringToActor)
    }
    clearEvents()
    openGate("gate1")
    waitAndCheck {
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    }
  }


  trait WithThreeSyseventsAvailAndOpenNotAckingGate extends WithEventsourceActivatedAndGateCreated {
    publishEventFromEventsource(EventFrame("eventId" -> "1"))
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    clearEvents()
    publishEventFromEventsource(EventFrame("eventId" -> "2"))
    openGate("gate1")
    publishEventFromEventsource(EventFrame("eventId" -> "3"))
    waitAndCheck {
      expectExactlyNEvents(3, GateStubActor.MessageReceivedAtGate)
    }
    expectExactlyNEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
  }

  "when eventsource is up gate is open and 3 events available, Eventsource" should "produce a single event at the gate if gate not acking" in new WithThreeSyseventsAvailAndOpenNotAckingGate {
  }

  it should "attempt redelivering that single message" in new WithThreeSyseventsAvailAndOpenNotAckingGate {
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 0)
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 1)
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 2)
    expectExactlyNEvents(3, GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectExactlyNEvents(3, GateStubActor.MessageReceivedAtGate, 'EventId -> "2")
  }

  it should "not attempt redelivery if gate acked as received" in new WithThreeSyseventsAvailAndOpenNotAckingGate {
    autoAckAsReceivedAtGate("gate1")
    duringPeriodInMillis(3000) {
      expectNoEvents(SubscriberBoundaryInitiatingActor.DeliveryAttempt, 'Attempt -> 2)
    }
  }

  it should "deliver all messages if gate is acking as processed" in new WithThreeSyseventsAvailAndOpenNotAckingGate {
    autoAckAsReceivedAtGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectExactlyNEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "2")
    expectExactlyNEvents(1, GateStubActor.MessageReceivedAtGate, 'EventId -> "3")
  }


  trait WithOneSyseventsAvailAndClosedGate extends WithEventsourceActivatedAndGateCreated {
    publishEventFromEventsource(EventFrame("eventId" -> "1"))
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.ScheduledForDelivery)
    clearEvents()
  }

  "when eventsource is up gate is closed and 1 event available, Eventsource" should "reconnect to the gate if connection drops" in new WithOneSyseventsAvailAndClosedGate {
    restartHubNode1()
    startGateStub1("gate1")
    expectOneOrMoreEvents(AgentProxyActor.EventsourceProxyUp)
    expectOneOrMoreEvents(GateStubActor.GateStatusCheckReceived)
    clearEvents()
    openGate("gate1")
    expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
  }

  it should "communicate the current state once reconnected" in new WithOneSyseventsAvailAndClosedGate {
    clearEvents()
    restartHubNode1()
    startGateStub1("gate1")
    expectOneOrMoreEvents(AgentProxyActor.EventsourceProxyUp)
    expectOneOrMoreEvents(GateStubActor.GateStatusCheckReceived)
    openGate("gate1")
    expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "1")
    expectOneOrMoreEvents(EventsourceProxyActor.InfoUpdate)
    val infoUpdate = locateFirstEventFieldValue(EventsourceProxyActor.InfoUpdate, "Data").asInstanceOf[String]
    Json.parse(infoUpdate) ~> 'state should be(Some("active"))
  }

  trait With100Sysevents extends WithEventsourceActivatedAndGateCreated {
    (1 to 100).foreach { i =>
      publishEventFromEventsource(EventFrame("eventId" -> i.toString))
    }

  }

  "when eventsource is up and 100 events available, Eventsource" should "deliver all messages when gate is immediatelly up" in new With100Sysevents {
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")

    (1 to 100).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "deliver all messages when gate is eventually up - with a delay" in new With100Sysevents {
    Thread.sleep(3000)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 100).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }
  it should "deliver all messages even if hub restarts in between" in new With100Sysevents {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartHubNode1()
    startGateStub1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (26 to 100).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "deliver all messages even if hub restarts multiple times in between" in new With100Sysevents {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartHubNode1()
    autoCloseGateAfter("gate1", 25)
    startGateStub1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (26 to 50).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    restartHubNode1()
    startGateStub1("gate1")
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (51 to 100).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  it should "stop delivering messages as soon as gate closes" in new With100Sysevents {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "99")
    }
  }

  it should "stop delivering messages as soon as gate closes - and resume delivery as soon as gate opens again" in new With100Sysevents {
    autoCloseGateAfter("gate1", 25)
    openGate("gate1")
    autoAckAsProcessedAtGate("gate1")
    (1 to 25).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
    duringPeriodInMillis(2000) {
      expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "99")
    }
    openGate("gate1")
    (1 to 100).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString)
    }
  }

  "AgentProxy" should "create eventsource on command" in new WithAgentNode1 with WithHubNode1  {
    implicit val sys = hub1System
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
  }

  it should "create be able to create multiple eventsources" in new WithAgentNode1 with WithHubNode1  {
    implicit val sys = hub1System
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    val dsProxy1Route = locateFirstEventFieldValue(EventsourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    clearEvents()
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate2")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    val dsProxy2Route = locateFirstEventFieldValue(EventsourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    dsProxy1Route should not be dsProxy2Route
  }


  trait WithSubscriberForAgentProxy extends WithAgentNode1 with WithHubNode1  {
    implicit val sys = hub1System
    val agentProxyRoute = ComponentKey(locateLastEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String])
    withSystem(HubSystemPrefix, 1) { sys =>
      startMessageSubscriber1(sys)
    }
  }

  it should "accept a subscriber for the list" in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_LIST))
    expectOneOrMoreEvents(AgentProxyActor.NewSubjectSubscription)
    expectOneOrMoreEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the list subscriber"  in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_LIST))
    expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    val data = Json.parse(locateFirstEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String])
    data.as[JsArray].value should be(empty)
  }


  it should "send updates to the list subscriber when eventsources are created" in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_LIST))
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    clearEvents()
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    waitAndCheck {
      expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    }
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]).as[JsArray].value should have size 1
    clearEvents()
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate2")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    waitAndCheck {
      expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    }
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]).as[JsArray].value should have size 2
  }


  it should "accept a subscriber for the info" in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_INFO))
    expectOneOrMoreEvents(AgentProxyActor.NewSubjectSubscription)
    expectOneOrMoreEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the info subscriber"  in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_INFO))
    expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]) ~> 'name should be (Some("agent1"))
  }

  it should "accept a subscriber for the configtpl" in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_CONFIGTPL))
    expectOneOrMoreEvents(AgentProxyActor.NewSubjectSubscription)
    expectOneOrMoreEvents(AgentProxyActor.FirstSubjectSubscriber)
  }

  it should "respond to the configtpl subscriber" in new WithSubscriberForAgentProxy {
    subscribeFrom1(hub1System, LocalSubj(agentProxyRoute, T_CONFIGTPL))
    expectOneOrMoreEvents(AgentProxyActor.UpdateForSubject)
    Json.parse(locateLastEventFieldValue(AgentProxyActor.UpdateForSubject, "Data").asInstanceOf[String]) ~> "title" should not be None
  }



  trait WithTwoEventsources extends WithAgentNode1 with WithHubNode1  {
    val route = locateFirstEventFieldValue(AgentProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate1")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    val dsProxy1Route = locateFirstEventFieldValue(EventsourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    var ds1PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    var ds1ComponentKey = locateFirstEventFieldValue(EventsourceActor.PreStart, "ComponentKey").asInstanceOf[String]

    clearEvents()
    sendCommand(hub1System, route, T_ADD, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate2")))
    expectExactlyNEvents(1, AgentProxyActor.EventsourceProxyUp)
    val dsProxy2Route = locateFirstEventFieldValue(EventsourceProxyActor.PreStart, "ComponentKey").asInstanceOf[String]
    var ds2PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }
    var ds2ComponentKey = locateFirstEventFieldValue(EventsourceActor.PreStart, "ComponentKey").asInstanceOf[String]
    dsProxy1Route should not be dsProxy2Route

    startGateStub1("gate1")
    startGateStub1("gate2")
    autoAckAsProcessedAtGate("gate1")
    autoAckAsProcessedAtGate("gate2")
    openGate("gate1")
    openGate("gate2")

    clearEvents()

    def publishEventFromEventsource1(j: EventFrame) = ds1PublisherActorRef ! j

    def publishEventFromEventsource2(j: EventFrame) = ds2PublisherActorRef ! j
  }

  "when two eventsources created, and both gates available, AgentProxy" should "be able to activate one"  in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
    }

    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(1, EventsourceActor.BecomingActive)
    expectExactlyNEvents(1, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)

  }

  it should "be able to activate another" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(1, EventsourceActor.BecomingActive)
    expectExactlyNEvents(1, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.BecomingActive)

  }
  it should "be able to activate both" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, EventsourceActor.BecomingActive)
    expectExactlyNEvents(2, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)

  }

  it should "be able to activate both and then stop one" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, EventsourceActor.BecomingActive)
    expectExactlyNEvents(2, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(hub1System, dsProxy1Route, T_STOP, None)
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)
    expectExactlyNEvents(1, EventsourceActor.BecomingPassive, 'ComponentKey -> ds1ComponentKey)
    waitAndCheck {
      expectNoEvents(EventsourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }

  }

  it should "be able to activate both and then remove one" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, EventsourceActor.BecomingActive)
    expectExactlyNEvents(2, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(hub1System, dsProxy1Route, T_REMOVE, None)
    expectExactlyNEvents(1, EventsourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectExactlyNEvents(1, EventsourceProxyActor.PostStop)
    waitAndCheck {
      expectNoEvents(EventsourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }
    sendCommand(hub1System, dsProxy2Route, T_STOP, None)
    expectOneOrMoreEvents(SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)

  }

  it should "be able to activate both and then remove both" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, EventsourceActor.BecomingActive)
    expectExactlyNEvents(2, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(hub1System, dsProxy1Route, T_REMOVE, None)
    sendCommand(hub1System, dsProxy2Route, T_REMOVE, None)
    expectExactlyNEvents(1, EventsourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectExactlyNEvents(1, EventsourceActor.PostStop, 'ComponentKey -> ds2ComponentKey)
    expectExactlyNEvents(2, EventsourceProxyActor.PostStop)

  }

  it should "be able to remove both" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_REMOVE, None)
    sendCommand(hub1System, dsProxy2Route, T_REMOVE, None)
    expectExactlyNEvents(1, EventsourceActor.PostStop, 'ComponentKey -> ds1ComponentKey)
    expectExactlyNEvents(1, EventsourceActor.PostStop, 'ComponentKey -> ds2ComponentKey)
    expectExactlyNEvents(2, EventsourceProxyActor.PostStop)

  }

  it should "be able to activate both and then stop both" in new WithTwoEventsources {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    waitAndCheck {
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate2")
      expectExactlyNEvents(1, GateStubActor.GateStatusCheckReceived, 'GateName -> "gate1")
    }

    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.AssociationAttempt)
    expectExactlyNEvents(2, EventsourceActor.BecomingActive)
    expectExactlyNEvents(2, PublisherStubActor.BecomingActive)
    expectExactlyNEvents(2, SubscriberBoundaryInitiatingActor.BecomingActive)
    clearEvents()

    sendCommand(hub1System, dsProxy1Route, T_STOP, None)
    sendCommand(hub1System, dsProxy2Route, T_STOP, None)
    expectExactlyNEvents(2,SubscriberBoundaryInitiatingActor.GateStateMonitorStopped)
    expectExactlyNEvents(1, EventsourceActor.BecomingPassive, 'ComponentKey -> ds1ComponentKey)
    expectExactlyNEvents(1, EventsourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)

  }


  it should "terminate when agent terminates" in new WithTwoEventsources {
    restartAgentNode1()
    expectExactlyNEvents(2, EventsourceProxyActor.PostStop)
    expectExactlyNEvents(1, AgentProxyActor.PostStop)

  }

  it should "recreate actor hierarchy when agent reconnects" in new WithTwoEventsources {
    restartAgentNode1()
    expectExactlyNEvents(2, EventsourceProxyActor.PostStop)
    expectExactlyNEvents(1, AgentProxyActor.PostStop)
    expectOneOrMoreEvents(AgentProxyActor.PreStart)
  }





  trait WithTwoEventsourcesAnd10SyseventsForEach extends WithTwoEventsources {
    (1 to 10) foreach { i =>
      val v = EventFrame("eventId" -> i.toString)
      publishEventFromEventsource1(v)
      publishEventFromEventsource2(v)
    }
  }




  "when two eventsources created, both gates available, and 10 events available for publishing, Eventsource" should
    "publish 10 messages to gate1 when first activated, gate2 sould receive nothing"  in new WithTwoEventsourcesAnd10SyseventsForEach {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    (1 to 10).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
    }
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'GateName -> "gate2")

  }

  it should "publish 10 messages to gate2 when second activated, gate1 sould receive nothing" in new WithTwoEventsourcesAnd10SyseventsForEach {
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    (1 to 10).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'GateName -> "gate1")

  }

  it should "publish 10 messages each to gate1 and gate2 when both activated"  in new WithTwoEventsourcesAnd10SyseventsForEach {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)
    (1 to 10).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }

  }


  "when two eventsources created, both gates available, and 10 events available for publishing, AgentProxy" should
    "be able to activate both and then reset one once all messages are published" taggedAs OnlyThisTest in new WithTwoEventsourcesAnd10SyseventsForEach {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)

    (1 to 10).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    clearEvents()

    sendCommand(hub1System, dsProxy1Route, T_RESET, None)
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.PostStop)
    expectExactlyNEvents(1, PublisherStubActor.PostStop)
    expectExactlyNEvents(1, PublisherStubActor.PublisherStubStarted, 'InitialState -> "None")
    expectExactlyNEvents(1, PublisherStubActor.BecomingActive)

    expectExactlyNEvents(1, EventsourceActor.PostStop)

    waitAndCheck {
      expectNoEvents(EventsourceProxyActor.PostStop)
    }

  }

  trait WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 extends WithTwoEventsourcesAnd10SyseventsForEach {
    sendCommand(hub1System, dsProxy1Route, T_START, None)
    sendCommand(hub1System, dsProxy2Route, T_START, None)

    clearEvents()


    (1 to 10).foreach { i =>
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate1")
      expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> i.toString, 'GateName -> "gate2")
    }
    clearEvents()

    startGateStub1("gate3")
    autoAckAsProcessedAtGate("gate3")
    openGate("gate3")


    sendCommand(hub1System, dsProxy1Route, T_UPDATE_PROPS, Some(Json.obj("source" -> Json.obj("class" -> "stub"), "targetGate" -> "akka.tcp://hub@localhost:12521/user/gate3")))
    expectExactlyNEvents(1, SubscriberBoundaryInitiatingActor.PostStop)
    expectExactlyNEvents(1, PublisherStubActor.PostStop)
    expectExactlyNEvents(1, PublisherStubActor.BecomingActive)
    // the ref will change...
    ds1PublisherActorRef = withSystem[ActorSelection](AgentSystemPrefix, 1) { sys =>
      sys.underlyingSystem.actorSelection(locateLastEventFieldValue(PublisherStubActor.PreStart, "Path").asInstanceOf[String])
    }

    expectExactlyNEvents(1, EventsourceActor.PostStop)

    waitAndCheck {
      expectNoEvents(EventsourceProxyActor.PostStop)
      expectNoEvents(EventsourceActor.BecomingPassive, 'ComponentKey -> ds2ComponentKey)
    }
  }

  "when 2 ds activated, 10 events published from each, 1st eventsource reconfigured to point to gate3" should
    "restart publisher previous state as an initial state" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    expectExactlyNEvents(1, PublisherStubActor.PublisherStubStarted, 'InitialState -> "Some({\"id\":\"10\"})")
  }

  it should "publish to gate3 any new messages" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    clearEvents()
    publishEventFromEventsource1(EventFrame("eventId" -> "abc"))
    expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate3")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate1")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate2")

  }

  it should "2nd ds still publish to gate2" in new WithBothActivated10MsgPublishedAndOneReconfiguredForGate3 {
    clearEvents()
    publishEventFromEventsource2(EventFrame("eventId" -> "abc"))
    expectOneOrMoreEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate2")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate1")
    expectNoEvents(GateStubActor.MessageReceivedAtGate, 'EventId -> "abc", 'GateName -> "gate3")

  }




}

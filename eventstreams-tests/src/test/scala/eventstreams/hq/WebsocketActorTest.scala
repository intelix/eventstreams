package eventstreams.hq

import actors.WebsocketActor
import eventstreams.core.Tools.configHelper
import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.TopicKey
import eventstreams.support.RouteeComponentStubOps._
import eventstreams.support._
import org.scalatest.FlatSpec
import actors.WebsocketActor.{opSplitChar, msgSplitChar}
import play.api.libs.json.{JsArray, Json, JsValue}
import scalaz._
import Scalaz._

class WebsocketActorTest
  extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {

  trait WithFourNodes extends WithEngineNode1 with WithEngineNode2 with WithWebNode1 with WithWebNode2 with RouteeComponentStub

  trait WithFourNodesStarted extends WithFourNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1,web2", 'Node -> "engine1")
    expectSomeEventsWithTimeout(10000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1,web2", 'Node -> "engine2")
    expectSomeEventsWithTimeout(10000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1,web2", 'Node -> "web1")
    expectSomeEventsWithTimeout(10000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1,web2", 'Node -> "web2")
    startRouteeComponentStub1(engine1System)
    startRouteeComponentStub2(engine1System)
    clearEvents()
  }

  "Three nodes with message router on each, Websocket Actor" should "start when accepting connection" in new WithFourNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1,web2", 'Node -> "engine1")
    startWebsocketActor1()
    expectSomeEvents(WebsocketActor.PreStart)
    expectSomeEvents(WebsocketActor.AcceptedConnection)
  }

  it should "send client initial message with the node id" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketMessageReceived)
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
  }

  val user1Uuid = "user1id"
  val user2Uuid = "user2id"

  trait WithTwoWebsocketActors extends WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketActor.AcceptedConnection)
    expectSomeEvents(WebsocketClientStub.WebsocketMessageReceived)
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    sendToWebsocketOn1("X" + user1Uuid)

    val localAddress1 = locateLastEventFieldValue(WebsocketClientStub.WebsocketAddressReceived, "Value").asInstanceOf[String]
    clearEvents()
    startWebsocketActor2()
    expectSomeEvents(WebsocketActor.AcceptedConnection)
    expectSomeEvents(WebsocketClientStub.WebsocketMessageReceived)
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web2Address)
    sendToWebsocketOn2("X" + user2Uuid)
    val localAddress2 = locateLastEventFieldValue(WebsocketClientStub.WebsocketAddressReceived, "Value").asInstanceOf[String]
    clearEvents()
  }

  it should "ignore invalid payload - unknown type" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketOn1("a" + user1Uuid)
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)

    }
  }

  it should "ignore invalid payload - invalid compression identifier" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketRawOn1("a" + user1Uuid)
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)
    }
  }

  it should "ignore invalid payload - corrupted compression"in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketRawOn1("z" + user1Uuid)
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)
    }
  }

  it should "ignore invalid payload - blank payload" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketRawOn1("")
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)
    }
  }

  it should "ignore invalid payload - blank payload with flat type" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketRawOn1("f")
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)
    }
  }

  it should "ignore invalid payload - blank payload with compressed type" in new WithFourNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketAddressReceived, 'Value -> web1Address)
    clearEvents()
    sendToWebsocketRawOn1("z")
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketMessageReceived)
      expectNoEvents(WebsocketClientStub.PreRestart)
      expectNoEvents(WebsocketActor.PreRestart)
      expectNoEvents(MessageRouterActor.RouteAdded)
    }
  }


  it should "recognise X message, uncompressed" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("X" + user1Uuid)
    expectSomeEvents(WebsocketActor.UserUUID, 'UUID -> user1Uuid)
  }
  it should "recognise X message, compressed" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("X" + user1Uuid, compressed = true)
    expectSomeEvents(WebsocketActor.UserUUID, 'UUID -> user1Uuid)
  }

  it should "react to X message by adding a new route" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("X" + user1Uuid, compressed = true)
    expectSomeEvents(MessageRouterActor.RouteAdded, 'Route -> user1Uuid)
  }




  it should "accept new location alias" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("Bl" + opSplitChar + localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "l", 'Location -> localAddress1)
  }

  it should "accept new location alias, compressed" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("Bl" + opSplitChar + localAddress1, compressed = true)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "l", 'Location -> localAddress1)
  }

  it should "accept new multi-char location alias" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("BABCdeFGH123" + opSplitChar + localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "ABCdeFGH123", 'Location -> localAddress1)
  }

  it should "accept two location aliases" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1 + msgSplitChar + "B2" + opSplitChar + "addr2")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "1", 'Location -> localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "2", 'Location -> "addr2")
  }

  it should "accept multiple location aliases" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1 + msgSplitChar + "B2" + opSplitChar + "addr2" + msgSplitChar + "B3" + opSplitChar + "addr3")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "1", 'Location -> localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "2", 'Location -> "addr2")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "3", 'Location -> "addr3")
  }

  it should "accept multiple location aliases as multiple messages too" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1)
    sendToWebsocketOn1("B2" + opSplitChar + "addr2")
    sendToWebsocketOn1("B3" + opSplitChar + "addr3")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "1", 'Location -> localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "2", 'Location -> "addr2")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "3", 'Location -> "addr3")
  }

  it should "accept location aliases overrides" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1)
    sendToWebsocketOn1("B1" + opSplitChar + "addr2")
    sendToWebsocketOn1("B3" + opSplitChar + "addr3")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "1", 'Location -> localAddress1)
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "1", 'Location -> "addr2")
    expectSomeEvents(WebsocketActor.NewLocationAlias, 'Name -> "3", 'Location -> "addr3")
  }

  def buildValidSubjectKey(locAlias: String, route: String, topic: String) = locAlias + opSplitChar + route + opSplitChar + topic
  def buildValidSubscribe(key: String) = "S" + key + opSplitChar
  def buildValidUnsubscribe(key: String) = "U" + key + opSplitChar
  def buildValidCommand(key: String, payload: Option[JsValue]) = "C" + key + opSplitChar + (payload.map(Json.stringify) | "")

  it should "accept new command alias" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1)
    sendToWebsocketOn1("A1" + opSplitChar + buildValidSubjectKey("1", "route", "topic"))
    expectSomeEvents(WebsocketActor.NewCmdAlias, 'Name -> "1", 'Path -> buildValidSubjectKey("1", "route", "topic"))
  }


  "Websocket client" should "be able to subscribe to the node manager updates" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + localAddress1)
    sendToWebsocketOn1("A1" + opSplitChar + buildValidSubjectKey("1", "cluster", "nodes"))
    sendToWebsocketOn1(buildValidSubscribe("1"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    val content = Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]).as[JsArray].value
    content should have size 4
  }

  it should "be able to subscribe to the node manager updates - regardless of the combination of the aliases used" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B2" + opSplitChar + localAddress1)
    sendToWebsocketOn1("AABC" + opSplitChar + buildValidSubjectKey("2", "cluster", "nodes"))
    sendToWebsocketOn1(buildValidSubscribe("ABC"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "ABC")
    val content = Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]).as[JsArray].value
    content should have size 4
  }

  it should "receive a cached update on the second subscription, if it comes from the same websocket" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B2" + opSplitChar + localAddress1)
    sendToWebsocketOn1("AABC" + opSplitChar + buildValidSubjectKey("2", "cluster", "nodes"))
    sendToWebsocketOn1(buildValidSubscribe("ABC"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "ABC")
    val content = Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]).as[JsArray].value
    content should have size 4
    clearEvents()
    sendToWebsocketOn1(buildValidSubscribe("ABC"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "ABC")
    expectSomeEvents(MessageRouterActor.RespondedWithCached)
  }

  it should "receive a cached update on the second subscription, if it comes from the different websocket" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B2" + opSplitChar + localAddress1)
    sendToWebsocketOn1("AABC" + opSplitChar + buildValidSubjectKey("2", "cluster", "nodes"))
    sendToWebsocketOn2("B2" + opSplitChar + localAddress1)
    sendToWebsocketOn2("AABC" + opSplitChar + buildValidSubjectKey("2", "cluster", "nodes"))
    sendToWebsocketOn1(buildValidSubscribe("ABC"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "ABC")
    val content = Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]).as[JsArray].value
    content should have size 4
    clearEvents()
    sendToWebsocketOn2(buildValidSubscribe("ABC"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "ABC")
    expectSomeEvents(MessageRouterActor.RespondedWithCached)
  }

  it should "subscribe to updates from a component on engine1" in new WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + engine1Address)
    sendToWebsocketOn1("A1" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "withresponse"))
    sendToWebsocketOn1(buildValidSubscribe("1"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]) ~> 'msg should be (Some("response"))
  }

  trait WithOneSubscription extends WithTwoWebsocketActors {
    sendToWebsocketOn1("B1" + opSplitChar + engine1Address)
    sendToWebsocketOn1("A1" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "withresponse"))
    sendToWebsocketOn1(buildValidSubscribe("1"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    clearEvents()
  }

  it should "receive any future updates from the component" in new WithOneSubscription {
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "test1")
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]) ~> 'msg should be (Some("test1"))
  }

  it should "a new subscription to component must get a latest update" in new WithOneSubscription {
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "test1")
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]) ~> 'msg should be (Some("test1"))
    clearEvents()
    sendToWebsocketOn2("B2" + opSplitChar + engine1Address)
    sendToWebsocketOn2("A2" + opSplitChar + buildValidSubjectKey("2", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "withresponse"))
    sendToWebsocketOn2(buildValidSubscribe("2"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2")
    Json.parse(locateLastEventFieldValue(WebsocketClientStub.WebsocketUpdateReceived, "Payload")
      .asInstanceOf[String]) ~> 'msg should be (Some("test1"))
  }

  trait WithTwoSubscriptions extends WithOneSubscription {
    sendToWebsocketOn2("B2" + opSplitChar + engine1Address)
    sendToWebsocketOn2("A2" + opSplitChar + buildValidSubjectKey("2", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "withresponse"))
    sendToWebsocketOn2(buildValidSubscribe("2"))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2")
    clearEvents()    
  }

  "two open and subscribed websockets" should "each receive update when it is published" in new WithTwoSubscriptions {
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "test2")

    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2", 'Payload -> "{\"msg\":\"test2\"}", 'InstanceId -> websocket2ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1", 'Payload -> "{\"msg\":\"test2\"}", 'InstanceId -> websocket1ClientId)

  }

  it should "be handle bulk updates" in new WithTwoSubscriptions {
    sendToWebsocketOn1("Aa" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "topic1"))
    sendToWebsocketOn1("Ab" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "topic2"))
    sendToWebsocketOn1("Ac" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "topic3"))
    sendToWebsocketOn1(buildValidSubscribe("a"))
    sendToWebsocketOn1(buildValidSubscribe("b"))
    sendToWebsocketOn1(buildValidSubscribe("c"))
    expectSomeEvents(3, RouteeComponentStubOps.FirstSubjectSubscriber)
    clearEvents()
    updateTopicFromRoutee1(engine1System, TopicKey("topic1"), "testA")
    updateTopicFromRoutee1(engine1System, TopicKey("topic2"), "testB")
    updateTopicFromRoutee1(engine1System, TopicKey("topic3"), "testC")

    waitAndCheck {
      expectSomeEvents(3, WebsocketClientStub.WebsocketUpdateReceived)
    }
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "a", 'Payload -> "{\"msg\":\"testA\"}", 'InstanceId -> websocket1ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "b", 'Payload -> "{\"msg\":\"testB\"}", 'InstanceId -> websocket1ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "c", 'Payload -> "{\"msg\":\"testC\"}", 'InstanceId -> websocket1ClientId)

  }

  it should "receive only last update if multiple updates arrive on the same topic " in new WithTwoSubscriptions {
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "testA")
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "testB")
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "testC")

    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2", 'Payload -> "{\"msg\":\"testC\"}", 'InstanceId -> websocket2ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1", 'Payload -> "{\"msg\":\"testC\"}", 'InstanceId -> websocket1ClientId)

  }

  it should "not receive any updates if update was on a different topic" in new WithTwoSubscriptions {
    updateTopicFromRoutee1(engine1System, TopicKey("withresponse2"), "test2")
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketUpdateReceived)
    }
  }

  it should "not receive any updates if update was from a different component even if topic name is the same" in new WithTwoSubscriptions {
    updateTopicFromRoutee2(engine1System, TopicKey("withresponse"), "test2")
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketUpdateReceived)
    }
  }

  it should "each receive stale notification when component dies" in new WithTwoSubscriptions {
    killRouteeComponentStub1(engine1System)
    expectSomeEvents(WebsocketClientStub.WebsocketStaleReceived, 'Alias -> "2")
    expectSomeEvents(WebsocketClientStub.WebsocketStaleReceived, 'Alias -> "1")
  }

  it should "resume receiving updates when died component resumes" in new WithTwoSubscriptions {
    killRouteeComponentStub1(engine1System)
    expectSomeEvents(WebsocketClientStub.WebsocketStaleReceived, 'Alias -> "2")
    expectSomeEvents(WebsocketClientStub.WebsocketStaleReceived, 'Alias -> "1")
    clearEvents()
    startRouteeComponentStub1(engine1System)
    expectSomeEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub1.key, 'InstanceAddress -> engine1Address)

    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2", 'Payload -> "{\"msg\":\"response\"}", 'InstanceId -> websocket2ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1", 'Payload -> "{\"msg\":\"response\"}", 'InstanceId -> websocket1ClientId)

    updateTopicFromRoutee1(engine1System, TopicKey("withresponse"), "test3")

    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2", 'Payload -> "{\"msg\":\"test3\"}", 'InstanceId -> websocket2ClientId)
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1", 'Payload -> "{\"msg\":\"test3\"}", 'InstanceId -> websocket1ClientId)
  }

  it should "be able to send command and get a response (websocket1)" in new WithTwoSubscriptions {
    sendToWebsocketOn1("Ac" + opSplitChar + buildValidSubjectKey(localAddress1, "_", "cmd"))
    sendToWebsocketOn2("Ac" + opSplitChar + buildValidSubjectKey(localAddress2, "_", "cmd"))
    sendToWebsocketOn1(buildValidSubscribe("c"))
    sendToWebsocketOn2(buildValidSubscribe("c"))


    sendToWebsocketOn1("ACmdOkWithMsg" + opSplitChar + buildValidSubjectKey("1", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "okwithmessage"))
    sendToWebsocketOn1(buildValidCommand("CmdOkWithMsg", Some(Json.obj("a"->123))))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "c", 'Payload -> "{\"ok\":{\"key\":\"okwithmessage\",\"msg\":\"message\"}}", 'InstanceId -> websocket1ClientId)
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "2")
    }
  }

  it should "be able to send command and get a response (websocket2)" in new WithTwoSubscriptions {
    sendToWebsocketOn1("Ac" + opSplitChar + buildValidSubjectKey(localAddress1, "_", "cmd"))
    sendToWebsocketOn2("Ac" + opSplitChar + buildValidSubjectKey(localAddress2, "_", "cmd"))
    sendToWebsocketOn1(buildValidSubscribe("c"))
    sendToWebsocketOn2(buildValidSubscribe("c"))


    sendToWebsocketOn2("ACmdOkWithMsg" + opSplitChar + buildValidSubjectKey("2", RouteeComponentStubOps.componentKeyForRouteeStub1.key, "okwithmessage"))
    sendToWebsocketOn2(buildValidCommand("CmdOkWithMsg", Some(Json.obj("a"->123))))
    expectSomeEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "c", 'Payload -> "{\"ok\":{\"key\":\"okwithmessage\",\"msg\":\"message\"}}", 'InstanceId -> websocket2ClientId)
    waitAndCheck {
      expectNoEvents(WebsocketClientStub.WebsocketUpdateReceived, 'Alias -> "1")
    }
  }


  // TODO all other negative scenarios
  
}
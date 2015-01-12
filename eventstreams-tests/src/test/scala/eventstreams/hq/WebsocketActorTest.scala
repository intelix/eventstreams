package eventstreams.hq

import actors.WebsocketActor
import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.support.{WebsocketClientStub, RouteeComponentStub, MultiNodeTestingSupport, SharedActorSystem}
import org.scalatest.FlatSpec

class WebsocketActorTest
  extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {

  trait WithThreeNodes extends WithEngineNode1 with WithEngineNode2 with WithWebNode1 with RouteeComponentStub

  trait WithThreeNodesStarted extends WithThreeNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1", 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1", 'Node -> "engine2")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1", 'Node -> "web1")
    startRouteeComponentStub1(engine1System)
    startRouteeComponentStub2(engine1System)
    clearEvents()
  }

  "Three nodes with message router on each, Websocket Actor" should "start when accepting connection" in new WithThreeNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,web1", 'Node -> "engine1")
    startWebsocketActor1()
    expectSomeEvents(WebsocketActor.PreStart)
    expectSomeEvents(WebsocketActor.AcceptedConnection)
  }

  it should "send client initial message with the node id" in new WithThreeNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketClientStub.WebsocketOutgoing)
    expectSomeEvents(WebsocketClientStub.AddressReceived, 'Value -> web1Address)
  }

  trait WithOneWebsocketActor extends WithThreeNodesStarted {
    startWebsocketActor1()
    expectSomeEvents(WebsocketActor.AcceptedConnection)
    expectSomeEvents(WebsocketClientStub.WebsocketOutgoing)
    clearEvents()
  }

  val userUuid = "12345678"
  
  it should "recognise X message, uncompressed" in new WithOneWebsocketActor {
    sendToWebsocketOn1("X" + userUuid)
    expectSomeEvents(WebsocketActor.UserUUID, 'UUID -> userUuid)
  }
  it should "recognise X message, compressed" in new WithOneWebsocketActor {
    sendToWebsocketOn1("X" + userUuid, compressed = true)
    expectSomeEvents(WebsocketActor.UserUUID, 'UUID -> userUuid)
  }

  it should "react to X message by adding a new route" in new WithOneWebsocketActor {
    sendToWebsocketOn1("X" + userUuid, compressed = true)
    expectSomeEvents(MessageRouterActor.RouteAdded, 'Route -> userUuid)
  }





}
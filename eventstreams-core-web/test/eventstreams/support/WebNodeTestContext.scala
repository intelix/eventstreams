package eventstreams.support

/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import actors.{RouterActor, LocalClusterAwareActor}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite

import scala.concurrent.Await
import scala.concurrent.duration.DurationDouble


trait WebNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val WebSystemPrefix = "web"
  val WebPlaySystemPrefix = "webplay"

  override def configs: Map[String, Config] = super.configs ++ Map(
    WebSystemPrefix + "1" -> ConfigFactory.load("web1-proc-test.conf"),
    WebSystemPrefix + "2" -> ConfigFactory.load("web2-proc-test.conf"),
    WebPlaySystemPrefix + "1" -> ConfigFactory.load("webplay1-proc-test.conf"),
    WebPlaySystemPrefix + "2" -> ConfigFactory.load("webplay2-proc-test.conf")
  )


  trait WithWebNode
    extends MessageRouterActorTestContext
    with ClusterTestContext
    with ClusterManagerActorTestContext
    with WebsocketActorTestContext {

    def websocketActorIdFor(systemIndex: Int) = "websocketActor" + systemIndex

    def startWebNode(systemIndex: Int) =
      withSystem(WebPlaySystemPrefix, systemIndex) { localSys =>
        withSystem(WebSystemPrefix, systemIndex) { implicit sys =>
          withCluster(sys) { cluster =>
            startMessageRouter(sys, cluster)
            startClusterManager(sys, cluster)

            localSys.start(LocalClusterAwareActor.props(cluster), LocalClusterAwareActor.id)
            localSys.start(RouterActor.props(Await.result(messageRouterActorSelection(sys).resolveOne(5.seconds), 5.seconds)), RouterActor.id)
          }
        }
      }

    def sendToWebsocketOn(systemIndex: Int, msg: String, compressed: Boolean): Unit = withSystem(WebPlaySystemPrefix, systemIndex) { localSys =>
      sendToWebsocket(localSys, websocketActorIdFor(systemIndex), msg, compressed)
    }
    def sendToWebsocketRawOn(systemIndex: Int, msg: String): Unit = withSystem(WebPlaySystemPrefix, systemIndex) { localSys =>
      sendToWebsocketRaw(localSys, websocketActorIdFor(systemIndex), msg)
    }

    def startWebsocketActor(systemIndex: Int): Unit =
      withSystem(WebPlaySystemPrefix, systemIndex) { localSys =>
        startWebsocketActor(localSys, websocketActorIdFor(systemIndex))
      }

    def restartWebNode(systemIndex: Int) = {
      destroySystem(WebSystemPrefix + systemIndex.toString)
      destroySystem(WebPlaySystemPrefix + systemIndex.toString)
      startWebNode(systemIndex)
    }

  }

  trait WithWebNode1 extends WithWebNode {
    def web1Address = "akka.tcp://engine@localhost:12541"

    def web1System = getSystem(WebSystemPrefix + "1")

    def localWeb1System = getSystem(WebPlaySystemPrefix + "1")

    def startWebNode1(): Unit = startWebNode(1)

    def restartWebNode1(): Unit = restartWebNode(1)

    def startWebsocketActor1() = startWebsocketActor(1)

    def sendToWebsocketOn1(msg: String, compressed: Boolean = false) = sendToWebsocketOn(1, msg, compressed)
    def sendToWebsocketRawOn1(msg: String) = sendToWebsocketRawOn(1, msg)

    def websocket1Id = websocketActorIdFor(1)

    def websocket1ClientId = "client_" + websocketActorIdFor(1)

    startWebNode1()
  }

  trait WithWebNode2 extends WithWebNode {
    def web2Address = "akka.tcp://engine@localhost:12542"

    def web2System = getSystem(WebSystemPrefix + "2")

    def localWeb2System = getSystem(WebPlaySystemPrefix + "2")

    def startWebNode2(): Unit = startWebNode(2)

    def restartWebNode2(): Unit = restartWebNode(2)

    def startWebsocketActor2() = startWebsocketActor(2)

    def sendToWebsocketOn2(msg: String, compressed: Boolean = false) = sendToWebsocketOn(2, msg, compressed)
    def sendToWebsocketRawOn2(msg: String) = sendToWebsocketRawOn(2, msg)

    def websocket2Id = websocketActorIdFor(2)

    def websocket2ClientId = "client_" + websocketActorIdFor(2)

    startWebNode2()
  }



}

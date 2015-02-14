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

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite


trait HubNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val HubSystemPrefix = "hub"

  override def configs: Map[String, Config] = super.configs ++ Map(
    HubSystemPrefix + "1" -> ConfigFactory.load("hub1-proc-test.conf"),
    HubSystemPrefix + "2" -> ConfigFactory.load("hub2-proc-test.conf")
  )


  trait WithHubNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with GateStubTestContext
    with SubscribingComponentStub
    with AgentManagerActorTestContext
    with ClusterTestContext
    with ClusterManagerActorTestContext
    with FlowManagerActorTestContext {

    def startHubNode(systemIndex: Int) =
      withSystem(HubSystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)
          withAgentManager(sys, cluster)
        }


        withConfigStorage(20 + systemIndex, sys)

      }

    def startGate(systemIndex: Int, name: String) = withSystem(HubSystemPrefix, systemIndex) { sys =>
      withGateStub(sys, name)
    }

    def restartHubNode(systemIndex: Int) = {
      destroySystem(HubSystemPrefix + systemIndex.toString)
      startHubNode(systemIndex)
    }

  }

  trait WithHubNode1 extends WithHubNode {
    def hub1Address = "akka.tcp://hub@localhost:12521"

    def hub1System = getSystem(HubSystemPrefix + "1")

    def startHubNode1(): Unit = startHubNode(1)

    def startGate1(name: String): Unit = startGate(1, name)

    def restartHubNode1(): Unit = restartHubNode(1)

    startHubNode1()
  }

  trait WithHubNode2 extends WithHubNode {
    def hub2Address = "akka.tcp://hub@localhost:12522"

    def hub2System = getSystem(HubSystemPrefix + "2")

    def startHubNode2(): Unit = startHubNode(2)

    def startGate2(name: String): Unit = startGate(2, name)

    def restartHubNode2(): Unit = restartHubNode(2)

    startHubNode2()
  }



}

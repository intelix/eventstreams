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


trait EngineNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val EngineSystemPrefix = "engine"

  override def configs: Map[String, Config] = super.configs ++ Map(
    EngineSystemPrefix + "1" -> ConfigFactory.load("engine1-proc-test.conf"),
    EngineSystemPrefix + "2" -> ConfigFactory.load("engine2-proc-test.conf")
  )


  trait WithEngineNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with GateStubTestContext
    with SubscribingComponentStub
    with AgentManagerActorTestContext
    with ClusterTestContext
    with ClusterManagerActorTestContext
    with FlowManagerActorTestContext {

    def startEngineNode(systemIndex: Int) =
      withSystem(EngineSystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)
        }


        withConfigStorage(20 + systemIndex, sys)
        withAgentManager(sys)

      }

    def startGate(systemIndex: Int, name: String) = withSystem(EngineSystemPrefix, systemIndex) { sys =>
      withGateStub(sys, name)
    }

    def restartEngineNode(systemIndex: Int) = {
      destroySystem(EngineSystemPrefix + systemIndex.toString)
      startEngineNode(systemIndex)
    }

  }

  trait WithEngineNode1 extends WithEngineNode {
    def engine1Address = "akka.tcp://engine@localhost:12521"

    def engine1System = getSystem(EngineSystemPrefix + "1")

    def startEngineNode1(): Unit = startEngineNode(1)

    def startGate1(name: String): Unit = startGate(1, name)

    def restartEngineNode1(): Unit = restartEngineNode(1)

    startEngineNode1()
  }

  trait WithEngineNode2 extends WithEngineNode {
    def engine2Address = "akka.tcp://engine@localhost:12522"

    def engine2System = getSystem(EngineSystemPrefix + "2")

    def startEngineNode2(): Unit = startEngineNode(2)

    def startGate2(name: String): Unit = startGate(2, name)

    def restartEngineNode2(): Unit = restartEngineNode(2)

    startEngineNode2()
  }



}

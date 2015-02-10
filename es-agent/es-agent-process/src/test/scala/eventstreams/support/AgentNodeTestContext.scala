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

import akka.actor.ActorRef
import com.typesafe.config.{ConfigFactory, Config}
import org.scalatest.Suite


trait AgentNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val AgentSystemPrefix = "agent"

  override def configs: Map[String, Config] = super.configs ++ Map(
    AgentSystemPrefix + "1" -> ConfigFactory.load("agent-proc-test.conf"),
    AgentSystemPrefix + "2" -> ConfigFactory.load("agent2-proc-test.conf")
  )

  trait WithAgentNode
    extends ConfigStorageActorTestContext
    with AgentControllerTestContext
    with AgentManagerActorTestContext {

    var agentControllerActor: Map[Int, ActorRef] = Map()

    def startAgentNode(systemIndex: Int) =
      withSystem(AgentSystemPrefix, systemIndex) { sys =>
        withConfigStorage(systemIndex, sys)
        agentControllerActor = agentControllerActor + (systemIndex -> withAgentController(sys))
      }

    def sendToAgentController(systemIndex: Int, msg: Any) = agentControllerActor.get(systemIndex).foreach(_ ! msg)

    def restartAgentNode(systemIndex: Int) = {
      destroySystem(AgentSystemPrefix + systemIndex.toString)
      agentControllerActor = agentControllerActor - systemIndex
      startAgentNode(systemIndex)
    }

  }

  trait WithAgentNode1 extends WithAgentNode {
    def startAgentNode1(): Unit = startAgentNode(1)

    def sendToAgentController1(msg: Any) = sendToAgentController(1, msg)

    def restartAgentNode1(): Unit = restartAgentNode(1)

    startAgentNode1()
  }

  trait WithAgentNode2 extends WithAgentNode {
    def startAgentNode2(): Unit = startAgentNode(2)

    def sendToAgentController2(msg: Any) = sendToAgentController(2, msg)

    def restartAgentNode2(): Unit = restartAgentNode(2)

    startAgentNode2()
  }



}

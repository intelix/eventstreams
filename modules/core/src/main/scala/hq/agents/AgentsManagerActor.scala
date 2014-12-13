/*
 * Copyright 2014 Intelix Pty Ltd
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

package hq.agents

import agent.shared.Handshake
import akka.actor._
import common.actors.{ActorObjWithoutConfig, ActorWithComposableBehavior, ActorWithDisassociationMonitor, SingleComponentActor}
import hq.{ComponentKey, TopicKey}
import play.api.libs.json.Json

object AgentsManagerActor extends ActorObjWithoutConfig {
  def id = "agents"

  def props = Props(new AgentsManagerActor())
}

case class AgentProxyAvailable(id: ComponentKey)


class AgentsManagerActor
  extends ActorWithComposableBehavior
  with SingleComponentActor
  with ActorWithDisassociationMonitor {

  var agents: Map[ComponentKey, ActorRef] = Map()

  def key = ComponentKey("agents")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  private def publishList() = T_LIST !! Some(Json.toJson(agents.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => publishList()
  }


  override def onTerminated(ref: ActorRef): Unit = {
    logger.info(s"Agent proxy terminated $ref")
    agents = agents.filter {
      case (name, otherRef) => otherRef != ref
    }
    publishList()

    super.onTerminated(ref)
  }

  private def handler: Receive = {
    case Handshake(ref, id) =>
      logger.info(s"Received handshake from $id ref $ref")
      context.watch(AgentProxyActor.start(key / id, ref))
    case AgentProxyAvailable(name) =>
      logger.info(s"Agent proxy confirmed $name")
      agents = agents + (name -> sender())
      publishList()
  }


}

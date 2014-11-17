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
import akka.remote.DisassociatedEvent
import common.actors.{ActorObjWithoutConfig, ActorWithComposableBehavior, SingleComponentActor}
import hq.{ComponentKey, TopicKey}
import play.api.libs.json.Json

object AgentsManagerActor extends ActorObjWithoutConfig {
  def id = "agents"

  def props = Props(new AgentsManagerActor())
}

case class AgentAvailable(id: ComponentKey)


class AgentsManagerActor
  extends ActorWithComposableBehavior
  with SingleComponentActor {

  var agents: Map[ComponentKey, ActorRef] = Map()

  def key = ComponentKey("agents")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def list = Some(Json.toJson(agents.keys.map { x => Json.obj("id" -> x.key)}.toArray))

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent]) // TODO refactor into the trait
    super.preStart()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => topicUpdate(T_LIST, list, singleTarget = Some(ref))
  }

  private def handler: Receive = {
    case Handshake(ref, name) =>
      logger.info("Received handshake from " + ref)
      context.watch(AgentProxyActor.start(key / name, ref))
    case AgentAvailable(name) =>
      agents = agents + (name -> sender())
      topicUpdate(T_LIST, list)
    case Terminated(ref) =>
      agents = agents.filter {
        case (name, otherRef) => otherRef != ref
      }
      topicUpdate(T_LIST, list)
  }


}

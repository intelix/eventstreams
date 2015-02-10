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

package eventstreams.agents

import akka.actor._
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors._
import eventstreams.core.agent.core.Handshake
import eventstreams.core.messages.{ComponentKey, TopicKey}
import play.api.libs.json.Json

trait AgentManagerActorEvents extends ComponentWithBaseEvents with BaseActorEvents with RouteeEvents {
    override def componentId: String = "Actor.AgentsManager"

  val AgentsManagerAvailable = 'AgentsManagerAvailable.info
  val HandshakeReceived = 'HandshakeReceived.info
  val AgentProxyTerminated = 'AgentProxyTerminated.info
  val AgentProxyInstanceAvailable = 'AgentProxyInstanceAvailable.info
  
}

object AgentsManagerActor extends ActorObjWithoutConfig with AgentManagerActorEvents {
  def id = "agents"

  def props = Props(new AgentsManagerActor())
}

case class AgentProxyAvailable(id: ComponentKey)


class AgentsManagerActor
  extends ActorWithComposableBehavior
  with RouteeActor
  with ActorWithDisassociationMonitor
  with AgentManagerActorEvents with WithEventPublisher {

  var agents: Map[ComponentKey, ActorRef] = Map()

  def key = ComponentKey("agents")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('ComponentKey -> AgentsManagerActor.id)

  override def preStart(): Unit = {
    super.preStart()
    AgentsManagerAvailable >> ('Path -> self )
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => publishList()
  }

  override def onTerminated(ref: ActorRef): Unit = {
    AgentProxyTerminated >> ('Actor -> ref)
    agents = agents.filter {
      case (name, otherRef) => otherRef != ref
    }
    publishList()

    super.onTerminated(ref)
  }

  private def publishList() = T_LIST !! Some(Json.toJson(agents.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))

  private def handler: Receive = {
    case Handshake(ref, id) =>
      HandshakeReceived >> ('AgentActor -> ref.path, 'AgentID -> id)
      context.watch(AgentProxyActor.start(key / id, ref))
    case AgentProxyAvailable(name) =>
      AgentProxyInstanceAvailable >> ('Name -> name, 'Actor -> sender())
      agents = agents + (name -> sender())
      publishList()
  }


}

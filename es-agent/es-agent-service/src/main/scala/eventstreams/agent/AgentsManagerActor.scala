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

package eventstreams.agent

import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.{ComponentKey, TopicKey}
import play.api.libs.json.Json

trait AgentManagerActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with RouteeSysevents {
    override def componentId: String = "Agent.ProxyManager"

  val AgentsManagerAvailable = 'AgentsManagerAvailable.info
  val HandshakeReceived = 'HandshakeReceived.info
  val AgentProxyTerminated = 'AgentProxyTerminated.info
  val AgentProxyInstanceAvailable = 'AgentProxyInstanceAvailable.info
  
}

object AgentsManagerActor extends ActorObj with AgentManagerActorSysevents {
  def id = "agents"

  def props(config: Config, cluster: Cluster) = Props(new AgentsManagerActor(config, cluster))
}


case class AgentProxyAvailable(id: ComponentKey)


class AgentsManagerActor(config: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with RouteeActor
  with ActorWithDisassociationMonitor
  with AgentManagerActorSysevents with WithSyseventPublisher {

  val id = AgentsManagerActor.id

  var agents: Map[ComponentKey, ActorRef] = Map()

  def key = ComponentKey(id)

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('ComponentKey -> id)

  override def preStart(): Unit = {
    super.preStart()
    AgentsManagerAvailable >> ('Path -> self )
  }

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
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

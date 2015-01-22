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

package eventstreams.core.components.cluster

import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.events.EventOps.{stringToEventOps, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools._
import eventstreams.core.actors._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._


trait ClusterManagerActorEvents
  extends ComponentWithBaseEvents {

  val ClusterStateChanged = "Cluster.StateChanged".info
  val ClusterMemberUp = "Cluster.MemberUp".trace
  val ClusterMemberDown = "Cluster.MemberDown".trace

  override def componentId: String = "Actor.ClusterManager"
}

object ClusterManagerActor extends ActorObjWithCluster with ClusterManagerActorEvents with WithEventPublisher {
  def id = "cluster"

  def props(implicit cluster: Cluster, config: Config) = Props(new ClusterManagerActor())
}

class ClusterManagerActor(implicit val cluster: Cluster, config: Config)
  extends ActorWithComposableBehavior
  with ClusterManagerActorEvents
  with ActorWithClusterPeers
  with RouteeActor {

  override val nodeName = config.as[Option[String]]("eventstreams.node.name") | myAddress

  val T_NODES = TopicKey("nodes")

  def key = ComponentKey(ClusterManagerActor.id)

  override def commonBehavior: Actor.Receive = super.commonBehavior

  def confirmedPeersNames = confirmedPeers.map{ case (node,json) => json ~> 'name | node.address.toString }




  override def onClusterMemberUp(info: NodeInfo): Unit = {
    ClusterMemberUp >> ('NodeInfo -> info)
    super.onClusterMemberUp(info)
  }

  override def onClusterMemberRemoved(info: NodeInfo): Unit = {
    ClusterMemberDown >> ('NodeInfo -> info)
    super.onClusterMemberRemoved(info)
  }

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('ComponentKey -> key.key, 'Node -> nodeName, 'ClusterAddress -> myAddress)

  override def onConfirmedPeersChanged(): Unit = {
    ClusterStateChanged >> ('Peers -> confirmedPeersNames.sorted.mkString(","))
    T_NODES !! nodesList
  }

  def nodesList = Some(Json.toJson(confirmedPeers.map { case (node, data) =>
    Json.obj(
      "id" -> node.address.toString,
      "address" -> node.address.toString,
      "name" -> (data ~> 'name | node.address.toString),
      "state" -> status2string(node.state),
      "roles" -> node.roles
    )
  }.toArray))

  def status2string(status: NodeState): String = status match {
    case Up() => "up"
    case Unreachable() => "unreachable"
    case x => "unknown"
  }


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_NODES =>
      T_NODES !! nodesList
  }

  override def peerData: JsValue = Json.obj(
    "name" -> nodeName
  )

}

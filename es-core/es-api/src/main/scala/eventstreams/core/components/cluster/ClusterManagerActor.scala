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
import core.sysevents.SyseventOps.stringToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools._
import eventstreams.TopicKey
import eventstreams.core.actors._
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._


trait ClusterManagerActorSysevents
  extends ComponentWithBaseSysevents {

  val ClusterStateChanged = "Cluster.StateChanged".info
  val ClusterMemberUp = "Cluster.MemberUp".trace
  val ClusterMemberDown = "Cluster.MemberDown".trace

  override def componentId: String = "ClusterManager"
}

object ClusterManagerActor extends ActorObjWithCluster with ClusterManagerActorSysevents with WithSyseventPublisher {
  def id = "unsecured_cluster"

  def props(implicit cluster: Cluster, config: Config) = Props(new ClusterManagerActor())
}

class ClusterManagerActor(implicit val cluster: Cluster, config: Config)
  extends ActorWithComposableBehavior
  with ClusterManagerActorSysevents
  with ActorWithClusterPeers
  with RouteeActor {

  val nodeName = config.as[Option[String]]("eventstreams.node.name") | myAddress

  val T_NODES = TopicKey("nodes")


  override def entityId: String = ClusterManagerActor.id

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

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('ComponentKey -> entityId, 'Node -> nodeName, 'ClusterAddress -> myAddress)

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


  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_NODES =>
      T_NODES !! nodesList
  }

  override def peerData: JsValue = Json.obj(
    "name" -> nodeName
  )

}

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

package hq.cluster

import akka.actor._
import akka.cluster.Cluster
import common.actors._
import hq.{ComponentKey, TopicKey}
import play.api.libs.json.Json

/**
 * Created by maks on 6/11/2014.
 */
object ClusterManagerActor extends ActorObjWithCluster {
  def id = "cluster"

  def props(implicit cluster: Cluster) = Props(new ClusterManagerActor())
}

class ClusterManagerActor(implicit val cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithClusterAwareness
  with SingleComponentActor {

  val T_NODES = TopicKey("nodes")

  def key = ComponentKey(ClusterManagerActor.id)

  override def commonBehavior: Actor.Receive = super.commonBehavior

  override def onClusterChangeEvent(): Unit = {
    logger.info("!>>> Cluster state changed")
    topicUpdate(T_NODES, nodesList)
  }

  def nodesList = Some(Json.toJson(nodes.map { x =>
    Json.obj(
      "id" -> x.address.toString,
      "address" -> x.address.toString,
      "state" -> status2string(x.state),
      "roles" -> x.roles
    )
  }.toArray))

  def status2string(status: NodeState): String = status match {
    case Up() => "up"
    case Unreachable() => "unreachable"
    case x => "unknown"
  }


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_NODES =>
      logger.debug(s"!>> $ref Subscribed to list of nodes")
      topicUpdate(T_NODES, nodesList, singleTarget = Some(ref))
  }


}

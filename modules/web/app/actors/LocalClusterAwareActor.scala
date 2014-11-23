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

package actors

import akka.actor.{ActorRefFactory, Address, Props}
import akka.cluster.Cluster
import common.actors.{ActorObj, ActorWithComposableBehavior}

object LocalClusterAwareActor extends ActorObj {
  override def id: String = "cluster"

  def props(cluster: Cluster) = Props(new LocalClusterAwareActor(cluster))

  def start(cluster: Cluster)(implicit f: ActorRefFactory) = f.actorOf(props(cluster), id)
}

case class InfoRequest()

case class InfoResponse(address: Address)

class LocalClusterAwareActor(cluster: Cluster) extends ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    logger.debug(s"Starting local cluster aware actor, our address: ${cluster.selfAddress}")
    super.preStart()
  }

  private def handler: Receive = {
    case InfoRequest() => sender() ! InfoResponse(cluster.selfAddress)
  }

}

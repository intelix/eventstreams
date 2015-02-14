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

package actors

import akka.actor.{ActorRefFactory, Address, Props}
import akka.cluster.Cluster
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{ActorObj, ActorWithComposableBehavior}

trait LocalClusterAwarenessSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Private.ClusterAwareness"
}

object LocalClusterAwareActor extends ActorObj {
  override def id: String = "cluster"

  def props(cluster: Cluster) = Props(new LocalClusterAwareActor(cluster))

  def start(cluster: Cluster)(implicit f: ActorRefFactory) = f.actorOf(props(cluster), id)
}

case class InfoRequest()

case class InfoResponse(address: Address)

class LocalClusterAwareActor(cluster: Cluster)
  extends ActorWithComposableBehavior
  with LocalClusterAwarenessSysevents
  with WithSyseventPublisher {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  private def handler: Receive = {
    case InfoRequest() => sender() ! InfoResponse(cluster.selfAddress)
  }

}

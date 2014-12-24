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

package eventstreams.core.actors

import akka.actor.{Actor, Address}
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

case class ClusterPeerHandshake()

case class ClusterPeerHandshakeResponse(map: JsValue)

trait ActorWithClusterPeers extends ActorWithClusterAwareness {


  private val peers = mutable.Map[Address, JsValue]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def peerData: JsValue

  def onConfirmedPeersChanged(): Unit = {}

  def confirmedPeers = nodes.filter { info => peers.contains(info.address)}.map { each => (each, peers.getOrElse(each.address, Json.obj()))}

  override def onClusterMemberUp(info: NodeInfo): Unit = {
    if (info.address == cluster.selfAddress) {
      addPeer(info.address, peerData)
    } else {
      logger.debug(s"Trying to talk to ${self.path.toStringWithAddress(info.address)}")
      context.actorSelection(self.path.toStringWithAddress(info.address)) ! ClusterPeerHandshake()
    }
    super.onClusterMemberUp(info)
  }

  override def onClusterMemberRemoved(info: NodeInfo): Unit = {
    super.onClusterMemberRemoved(info)
    peers.remove(info.address)
    onConfirmedPeersChanged()
  }

  private def addPeer(address: Address, data: JsValue) = {
    peers.put(address, data)
    onConfirmedPeersChanged()
  }


  private def handler: Receive = {
    case ClusterPeerHandshake() =>
      logger.debug(s"Cluster peer handshake request from ${sender()}")
      sender() ! ClusterPeerHandshakeResponse(peerData)
    case ClusterPeerHandshakeResponse(response) =>
      logger.debug(s"Cluster peer handshake response from ${sender()}")
      addPeer(sender().path.address, response)
  }

}

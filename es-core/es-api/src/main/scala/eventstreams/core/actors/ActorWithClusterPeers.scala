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

package eventstreams.core.actors

import akka.actor.{Actor, Address}
import core.sysevents.SyseventOps.stringToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.CommMessage

import scala.collection.mutable


trait ActorWithClusterPeersSysevents extends ComponentWithBaseSysevents {
  val ClusterHandshakingWith = "Cluster.HandshakingWith".trace
  val ClusterConfirmedPeer = "Cluster.ConfirmedPeer".info
  val ClusterPeerHandshakeReceived = "Cluster.PeerHandshakeReceived".trace
}

case class ClusterPeerHandshake() extends CommMessage

case class ClusterPeerHandshakeResponse(map: Any) extends CommMessage


trait ActorWithClusterPeers[T] extends ActorWithClusterAwareness with ActorWithClusterPeersSysevents with ActorWithTicks {
  _: WithSyseventPublisher =>

  private val peers = mutable.Map[Address, T]()
  private var pendingPeers = Set[Address]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def peerData: T

  def onConfirmedPeersChanged(): Unit = {}

  def confirmedPeers = nodes.filter { info => peers.contains(info.address)}.collect {
    case next if peers.contains(next.address) => (next, peers.get(next.address))
  }

  override def onClusterMemberUp(info: NodeInfo): Unit = {
    if (info.address == cluster.selfAddress) {
      addPeer(info.address, peerData)
    } else {
      pendingPeers = pendingPeers + info.address
      handshakeWith(info.address)
      context.actorSelection(self.path.toStringWithAddress(info.address)) ! ClusterPeerHandshake()
    }
    super.onClusterMemberUp(info)
  }

  private def handshakeWith(address: Address) = {
    ClusterHandshakingWith >> ('Peer -> self.path.toStringWithAddress(address))
    context.actorSelection(self.path.toStringWithAddress(address)) ! ClusterPeerHandshake()
  }
  
  override def onClusterMemberRemoved(info: NodeInfo): Unit = {
    super.onClusterMemberRemoved(info)
    peers.remove(info.address)
    pendingPeers = pendingPeers - info.address
    onConfirmedPeersChanged()
  }

  private def addPeer(address: Address, data: T) = peers get address match {
    case Some(existingData) if existingData == data =>
      pendingPeers = pendingPeers - address
    case _ =>
      peers.put(address, data)
      pendingPeers = pendingPeers - address
      onConfirmedPeersChanged()
  }


  override def internalProcessTick(): Unit = {
    pendingPeers foreach handshakeWith
    super.internalProcessTick()
  }

  private def handler: Receive = {
    case ClusterPeerHandshake() =>
      ClusterPeerHandshakeReceived >> ('Peer -> sender())
      sender() ! ClusterPeerHandshakeResponse(peerData)
    case ClusterPeerHandshakeResponse(response) =>
        ClusterConfirmedPeer >> ('Peer -> sender(), 'Info -> response)
        addPeer(sender().path.address, response.asInstanceOf[T])
  }

}

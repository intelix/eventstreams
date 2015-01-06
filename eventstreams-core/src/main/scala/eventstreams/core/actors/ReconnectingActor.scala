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

import akka.actor.ActorRef
import akka.remote.DisassociatedEvent
import core.events.EventOps.symbolToEventOps
import core.events.ref.ComponentWithBaseEvents

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.{Failure, Success}

trait ReconnectingActorEvents extends ComponentWithBaseEvents {
  val AssociationAttempt = 'AssociationAttempt.info
  val AssociatedWithRemoteActor = 'AssociatedWithRemoteActor.info
  val RemoteActorTerminated = 'RemoteActorTerminated.info
  val RemoteActorDisassociated = 'RemoteActorDisassociated.info
}


trait ReconnectingActor
  extends ActorWithComposableBehavior
  with WithRemoteActorRef
  with ActorWithDisassociationMonitor
  with ReconnectingActorEvents {

  implicit private val ec = context.dispatcher
  private var peer: Option[ActorRef] = None
  
  private var reconnectAttemptCounter = 0 

  override final def remoteActorRef: Option[ActorRef] = peer

  override def commonBehavior: Receive = handleReconnectMessages orElse super.commonBehavior

  def connectionEndpoint: String

  def monitorConnectionWithDeathWatch = false

  def remoteAssociationTimeout = 5.seconds

  def reconnectAttemptInterval = 3.seconds

  def connected: Boolean = peer.isDefined

  def onConnectedToEndpoint() = {}

  def onDisconnectedFromEndpoint() = {}

  def scheduleReconnect(duration: FiniteDuration = reconnectAttemptInterval) = {
    context.system.scheduler.scheduleOnce(duration, self, Associate())
  }

  def disconnect(): Unit = {
    if (peer.isDefined) {
      peer = None
      onDisconnectedFromEndpoint()
    }
  }

  def initiateReconnect(): Unit = {
    disconnect()
    val addr = actorSelection
    reconnectAttemptCounter += 1
    AssociationAttempt >>('Target -> addr, 'AttemptCount -> reconnectAttemptCounter)
    
    addr.resolveOne(remoteAssociationTimeout).onComplete {
      case Failure(x) => self ! AssociationFailed(x)
      case Success(ref) => self ! Connected(ref)
    }
  }


  override def onTerminated(ref: ActorRef): Unit = {
    if (peer.contains(ref)) {
      RemoteActorTerminated >>('Target -> ref)
      disconnect()
    }
    super.onTerminated(ref)
  }

  def handleReconnectMessages: Receive = {
    case Connected(ref) =>
      AssociatedWithRemoteActor >>('Target -> ref, 'AttemptCount -> reconnectAttemptCounter)
      reconnectAttemptCounter = 0
      peer = Some(ref)
      if (monitorConnectionWithDeathWatch) context.watch(ref)
      onConnectedToEndpoint()
    case Associate() =>
      initiateReconnect()
    case DisassociatedEvent(local, remote, inbound) =>
      RemoteActorDisassociated >>('Target -> remote)
      peer match {
        case Some(ref) if ref.path.address == remote => disconnect()
        case _ => ()
      }
    case AssociationFailed(x) =>
      scheduleReconnect()
  }

  private def actorSelection = context.actorSelection(connectionEndpoint)


}

private case class AssociationFailed(cause: Throwable)

private case class Connected(ref: ActorRef)

private case class Associate()

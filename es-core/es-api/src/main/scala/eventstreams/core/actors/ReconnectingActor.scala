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
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.ref.ComponentWithBaseSysevents

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

trait ReconnectingActorSysevents extends ComponentWithBaseSysevents {
  val AssociationAttempt = 'AssociationAttempt.info
  val AssociatedWithRemoteActor = 'AssociatedWithRemoteActor.info
  val RemoteActorTerminated = 'RemoteActorTerminated.info
  val RemoteActorDisassociated = 'RemoteActorDisassociated.info
}


trait ReconnectingActor
  extends ActorWithComposableBehavior
  with WithRemoteActorRef
  with ActorWithDisassociationMonitor
  with ReconnectingActorSysevents {

  implicit private val ec = context.dispatcher
  private var peer: Option[ActorRef] = None
  
  private var reconnectAttemptCounter = 0 

  override final def remoteActorRef: Option[ActorRef] = peer

  override def commonBehavior: Receive = handleReconnectMessages orElse super.commonBehavior

  def connectionEndpoint: Option[String]

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

    actorSelection match {
      case None => scheduleReconnect()
      case Some(addr) =>
        reconnectAttemptCounter += 1
        AssociationAttempt >>('Target -> addr, 'AttemptCount -> reconnectAttemptCounter)

        addr.resolveOne(remoteAssociationTimeout).onComplete {
          case Failure(x) => self ! AssociationFailed(x)
          case Success(ref) => self ! Connected(ref)
        }
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
      peer match {
        case Some(ref) if ref.path.address == remote =>
          RemoteActorDisassociated >>('Target -> remote)
          disconnect()
        case _ => ()
      }
    case AssociationFailed(x) =>
      scheduleReconnect()
  }

  private def actorSelection = connectionEndpoint.map(context.actorSelection)


}

private case class AssociationFailed(cause: Throwable)

private case class Connected(ref: ActorRef)

private case class Associate()

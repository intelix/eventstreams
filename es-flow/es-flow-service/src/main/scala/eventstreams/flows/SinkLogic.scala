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
package eventstreams.flows

import _root_.core.sysevents.WithSyseventPublisher
import eventstreams._
import eventstreams.core.actors._
import eventstreams.gates.{UnregisterSink, RegisterSink}

private[flows] trait SinkLogic
  extends ActorWithComposableBehavior
  with ReconnectingActor
  with ActorWithActivePassiveBehaviors
  with WithSyseventPublisher {

  def onNextEvent(e: Acknowledgeable[_])

  override def monitorConnectionWithDeathWatch: Boolean = true

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def blockGateWhenPassive: Boolean

  override def preStart(): Unit = {
    super.preStart()
    initiateReconnect()
  }

  override def onBecameActive(): Unit = {
    if (!blockGateWhenPassive) remoteActorRef.foreach(_ ! RegisterSink(self))
    super.onBecameActive()
  }

  override def onBecamePassive(): Unit = {
    if (!blockGateWhenPassive) remoteActorRef.foreach(_ ! UnregisterSink(self))
    super.onBecamePassive()
  }

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    if (blockGateWhenPassive || isComponentActive) remoteActorRef.foreach(_ ! RegisterSink(self))
  }

  override def onDisconnectedFromEndpoint(): Unit = super.onDisconnectedFromEndpoint()

  private def handler: Receive = {
    case b : Acknowledgeable[_] if isComponentActive => onNextEvent(b)
    case b : Acknowledgeable[_] => ()
    case b: AcknowledgeAsProcessed => remoteActorRef.foreach(_ ! b)
    case b: AcknowledgeAsReceived => remoteActorRef.foreach(_ ! b)
  }



}

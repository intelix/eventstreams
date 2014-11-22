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

package agent.shared

import akka.actor.ActorRef
import play.api.libs.json.JsValue

trait WireMessage

trait AgentControllerMessage extends WireMessage
trait AgentMessage extends WireMessage

case class Acknowledgeable[T](msg: T, id: Long) extends AgentMessage
case class Acknowledge(id: Long) extends AgentMessage
case class MessageWithAttachments[T](msg: T, attachments: JsValue) extends AgentMessage


sealed trait GateState
case class GateClosed() extends GateState
case class GateOpen() extends GateState

case class GateStateCheck(ref: ActorRef)
case class GateStateUpdate(state: GateState)


case class Handshake(ref: ActorRef, name: String) extends AgentControllerMessage
case class CommunicationProxyRef(ref: ActorRef) extends AgentControllerMessage

case class GenericJSONMessage(json: String)


case class CreateTap(config: JsValue) extends AgentControllerMessage
case class ReconfigureTap(config: JsValue) extends AgentControllerMessage
case class RemoveTap() extends AgentControllerMessage
case class ResetTapState() extends AgentControllerMessage



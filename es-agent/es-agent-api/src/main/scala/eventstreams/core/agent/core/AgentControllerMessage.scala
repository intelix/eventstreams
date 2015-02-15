package eventstreams.core.agent.core

import akka.actor.ActorRef
import eventstreams.CommMessage
import play.api.libs.json.JsValue


trait AgentControllerMessage extends CommMessage

case class Handshake(ref: ActorRef, uuid: String) extends AgentControllerMessage

case class CommunicationProxyRef(ref: ActorRef) extends AgentControllerMessage

case class CreateEventsource(config: String) extends AgentControllerMessage

case class ReconfigureEventsource(config: String) extends AgentControllerMessage

case class RemoveEventsource() extends AgentControllerMessage

case class ResetEventsourceState() extends AgentControllerMessage



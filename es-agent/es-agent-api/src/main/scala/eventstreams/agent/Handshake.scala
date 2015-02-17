package eventstreams.agent

import akka.actor.ActorRef


case class Handshake(ref: ActorRef, uuid: String) extends AgentControllerMessage

package eventstreams.agent

import akka.actor.ActorRef


case class CommunicationProxyRef(ref: ActorRef) extends AgentControllerMessage

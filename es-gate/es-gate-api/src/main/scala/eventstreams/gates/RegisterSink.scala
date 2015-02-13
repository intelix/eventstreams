package eventstreams.gates

import akka.actor.ActorRef


case class RegisterSink(sinkRef: ActorRef)
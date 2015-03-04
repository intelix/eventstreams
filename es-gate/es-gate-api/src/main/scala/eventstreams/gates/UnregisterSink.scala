package eventstreams.gates

import akka.actor.ActorRef


case class UnregisterSink(sinkRef: ActorRef)
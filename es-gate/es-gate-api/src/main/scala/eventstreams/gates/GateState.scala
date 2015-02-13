package eventstreams.gates

import akka.actor.ActorRef
import eventstreams.CommMessage

sealed trait GateState

case class GateClosed() extends GateState

case class GateOpen() extends GateState

case class GateStateCheck(ref: ActorRef) extends CommMessage

case class GateStateUpdate(state: GateState) extends CommMessage


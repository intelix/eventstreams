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

package eventstreams.instructions

import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.EventFrame
import eventstreams.core.actors.{ActorWithTicks, StoppableSubscribingPublisherActor}
import eventstreams.instructions.Types._

object SimpleInstructionWrappingActor {

  def props(instruction: SimpleInstructionTypeWithGenerator, maxInFlight: Int): Props = Props(new SimpleInstructionWrappingActor(instruction, maxInFlight))

  def start(f: ActorRefFactory, instruction: SimpleInstructionTypeWithGenerator, maxInFlight: Int = 1000) =
    f.actorOf(props(instruction, maxInFlight))

}


class SimpleInstructionWrappingActor(instruction: SimpleInstructionTypeWithGenerator, maxInFlight: Int)
  extends StoppableSubscribingPublisherActor
  with ActorWithTicks {

  val (onEvent, onTick) = instruction

  override def execute(value: EventFrame) = Some(onEvent(value))

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    if (isActive && isComponentActive) onTick foreach { onTickFunc => onTickFunc(millisTimeSinceStateChange) foreach pushSingleEventToStream}
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = pendingToDownstreamCount
  }

  override def componentId: String = "InstructionWrapper"
}

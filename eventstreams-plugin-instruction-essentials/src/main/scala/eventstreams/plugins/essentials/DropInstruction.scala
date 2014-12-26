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

package eventstreams.plugins.essentials

import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import eventstreams.core.{Fail, JsonFrame, Utils}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

trait DropInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val Dropped = 'DateParsed.trace

  override def componentId: String = "Instruction.Drop"
}

trait DropInstructionConstants extends InstructionConstants with DropInstructionEvents {
}


class DropInstruction extends SimpleInstructionBuilder with DropInstructionConstants with WithEventPublisher {
  val configId = "drop"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] = \/- {

    val uuid = Utils.generateShortUUID

    Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

    frame: JsonFrame => {
      val eventId = frame.event ~> 'eventId | "n/a"

      Dropped >>('EventId --> eventId, 'InstructionInstanceId --> uuid)
      List()
    }
  }
}


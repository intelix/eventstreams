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

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import play.api.libs.json.{JsValue, Json}

import scalaz._

trait DropInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Dropped = 'DateParsed.trace

  override def componentId: String = "Instruction.Drop"
}

trait DropInstructionConstants extends InstructionConstants with DropInstructionSysevents {
}


class DropInstruction extends SimpleInstructionBuilder with DropInstructionConstants with WithSyseventPublisher {
  val configId = "drop"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] = \/- {

    val uuid = UUIDTools.generateShortUUID

    Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

    frame: EventFrame => {
      val eventId = frame.eventIdOrNA

      Dropped >>('EventId -> eventId, 'InstructionInstanceId -> uuid)
      List()
    }
  }
}


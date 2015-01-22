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

package eventstreams.plugins.essentials

import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._


trait DropFieldInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val FieldDropped = 'FieldDropped.trace

  override def componentId: String = "Instruction.DropField"
}

trait DropFieldInstructionConstants extends InstructionConstants with DropFieldInstructionEvents {
  val CfgFFieldToDrop = "fieldToDrop"
}

class DropFieldInstruction extends SimpleInstructionBuilder with DropFieldInstructionConstants with WithEventPublisher {
  val configId = "dropfield"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldToDrop \/> Fail(s"Invalid $configId instruction. Missing '$CfgFFieldToDrop' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = Utils.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val field = macroReplacement(frame, fieldName)

        val eventId = frame.eventIdOrNA

        val value = if (field.startsWith("?")) {
          val actualFieldName = macroReplacement(frame, field.substring(1).toLowerCase)
          FieldDropped >>('Field -> actualFieldName, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
          frame.replaceAllExisting(actualFieldName, EventDataValueNil())
        } else {
          val path = macroReplacement(frame, field)
          FieldDropped >>('Field -> fieldName, 'Path -> path, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
          EventValuePath(path).setValueInto(frame, EventDataValueNil())
        }

        List(value)

      }
    }


}

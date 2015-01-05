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

import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json.{JsString, JsValue, Json}

import scalaz.Scalaz._
import scalaz._


trait EnrichInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val Enriched = 'Enriched.trace

  override def componentId: String = "Instruction.Enrich"
}

trait EnrichInstructionConstants extends InstructionConstants with EnrichInstructionEvents {
  val CfgFFieldToEnrich = "fieldToEnrich"
  val CfgFTargetValueTemplate = "targetValueTemplate"
  val CfgFTargetType = "targetType"
}

class EnrichInstruction extends SimpleInstructionBuilder with EnrichInstructionConstants with WithEventPublisher {
  val configId = "enrich"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldToEnrich \/> Fail(s"Invalid $configId instruction. Missing '$CfgFFieldToEnrich' value. Contents: ${Json.stringify(props)}")
    ) yield {
      val fieldValue = props #> CfgFTargetValueTemplate | JsString("")
      val fieldType = props ~> CfgFTargetType | "s"

      val uuid = Utils.generateShortUUID

      Built >>('Field -> fieldName, 'Value -> fieldValue, 'Type -> fieldType, 'InstructionInstanceId -> uuid)

      frame: JsonFrame => {

        val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])

        val replacement: JsValue = macroReplacement(frame, fieldValue)

        val value: JsValue = setValue(fieldType, replacement, keyPath, frame.event)

        val eventId = frame.event ~> 'eventId | "n/a"

        Enriched >>('Path -> keyPath, 'Replacement -> replacement, 'NewValue -> value, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

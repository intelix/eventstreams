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
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


trait ReplaceInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val Replaced = 'Replaced.trace

  override def componentId: String = "Instruction.Replace"
}

trait ReplaceInstructionConstants extends InstructionConstants with ReplaceInstructionEvents {
  val CfgFFieldName = "fieldName"
  val CfgFPattern = "pattern"
  val CfgFReplacementValue = "replacementValue"
}

object ReplaceInstructionConstants extends ReplaceInstructionConstants


class ReplaceInstruction extends SimpleInstructionBuilder with ReplaceInstructionConstants with WithEventPublisher {
  val configId = "replace"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldName \/> Fail(s"Invalid replace instruction. Missing '$CfgFFieldName' value. Contents: ${Json.stringify(props)}");
      pattern <- props ~> CfgFPattern \/> Fail(s"Invalid replace instruction. Missing '$CfgFPattern' value. Contents: ${Json.stringify(props)}");
      _ <- Try(new Regex(pattern)).toOption \/> Fail(s"Invalid replace instruction. Invalid '$CfgFPattern' value. Contents: ${Json.stringify(props)}")
    ) yield {
      val replacementValue = props #> CfgFReplacementValue | JsString("")

      val uuid = Utils.generateShortUUID

      Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

      frame: JsonFrame => {

        val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])

        val replacement = macroReplacement(frame, replacementValue).asOpt[String].getOrElse("")

        val originalValue = locateFieldValue(frame, fieldName).asOpt[String].getOrElse("")

        val newValue = originalValue.replaceAll(pattern, replacement)

        val value: JsValue = setValue("s", JsString(newValue), keyPath, frame.event)

        val eventId = frame.event ~> 'eventId | "n/a"

        Replaced >>('Path --> keyPath, 'NewValue --> newValue, 'EventId --> eventId, 'InstructionInstanceId --> uuid)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

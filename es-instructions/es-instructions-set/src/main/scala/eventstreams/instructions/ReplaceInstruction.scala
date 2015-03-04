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
import eventstreams.Tools.{configHelper, _}
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import play.api.libs.json.{JsValue, Json}

import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


trait ReplaceInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Replaced = 'Replaced.trace

  override def componentId: String = "Instruction.Replace"
}

trait ReplaceInstructionConstants extends InstructionConstants with ReplaceInstructionSysevents {
  val CfgFFieldName = "fieldName"
  val CfgFPattern = "pattern"
  val CfgFReplacementValue = "replacementValue"
}

object ReplaceInstructionConstants extends ReplaceInstructionConstants


class ReplaceInstruction extends SimpleInstructionBuilder with ReplaceInstructionConstants with WithSyseventPublisher {
  val configId = "replace"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldName \/> Fail(s"Invalid replace instruction. Missing '$CfgFFieldName' value. Contents: ${Json.stringify(props)}");
      pattern <- props ~> CfgFPattern \/> Fail(s"Invalid replace instruction. Missing '$CfgFPattern' value. Contents: ${Json.stringify(props)}");
      _ <- Try(new Regex(pattern)).toOption \/> Fail(s"Invalid replace instruction. Invalid '$CfgFPattern' value. Contents: ${Json.stringify(props)}")
    ) yield {
      val replacementValue = props ~> CfgFReplacementValue | ""

      val uuid = UUIDTools.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val keyPath = macroReplacement(frame, fieldName)

        val replacement = macroReplacement(frame, replacementValue)

        val originalValue = locateFieldValue(frame, fieldName)

        val newValue = originalValue.replaceAll(pattern, replacement)

        val value: EventFrame = setValue("s", newValue, keyPath, frame)

        val eventId = frame.eventIdOrNA

        Replaced >>('Path -> keyPath, 'NewValue -> newValue, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

        List(value)

      }
    }


}

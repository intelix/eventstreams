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

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.{configHelper, _}
import eventstreams._
import eventstreams.instructions.Types.SimpleInstructionType
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz._

trait CherrypickInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Cherrypicked = 'Cherrypicked.trace

  override def componentId: String = "Instruction.Cherrypick"
}

trait CherrypickInstructionConstants extends InstructionConstants with CherrypickInstructionSysevents {
  val CfgFFieldName = "fieldName"
  val CfgFFieldValuePath = "fieldValuePath"
  val CfgFEventIdTemplate = "eventIdTemplate"
  val CfgFEventSeqTemplate = "eventSeqTemplate"
  val CfgFKeepOriginal = "keepOriginalEvent"
  val CfgFAdditionalTags = "additionalTags"
  val CfgFIndex = "index"
  val CfgFTable = "table"
  val CfgFTTL = "ttl"
}

class CherrypickInstruction extends SimpleInstructionBuilder with CherrypickInstructionConstants with WithSyseventPublisher {
  val configId = "cherrypick"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldName orFail s"Invalid $configId instruction. Missing '$CfgFFieldName' value. Contents: ${Json.stringify(props)}";
      valuePath <- props ~> CfgFFieldValuePath orFail s"Invalid $configId instruction. Missing '$CfgFFieldValuePath' value. Contents: ${Json.stringify(props)}"
    ) yield {

      val eventIdTemplate = props ~> CfgFEventIdTemplate | "${eventId}_picked"
      val eventSeqTemplate = props ~> CfgFEventSeqTemplate | "${eventSeq}"
      val keepOriginalEvent = props ?> CfgFKeepOriginal | true
      val additionalTags = (props ~> CfgFAdditionalTags | "").split(",").map(_.trim)

      val index = props ~> CfgFIndex | "${index}"
      val table = props ~> CfgFTable | "${table}"
      val ttl = props ~> CfgFTTL | "${_ttl}"

      val uuid = UUIDTools.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val sourcePath = macroReplacement(frame, valuePath)
        val targetPath = macroReplacement(frame, fieldName)

        val result = if (keepOriginalEvent) List(frame) else List()

        EventValuePath(sourcePath).extractRaw(frame) match {
          case Some(v) =>
            val newEventId = macroReplacement(frame, eventIdTemplate)
            var newValue = EventValuePath(targetPath).setValueInto(EventFrame(), v) +
              ('eventId -> newEventId) +
              ('eventSeq -> macroReplacement(frame, eventSeqTemplate)) +
              ('index -> macroReplacement(frame, index)) +
              ('table -> macroReplacement(frame, table)) +
              ('_ttl -> macroReplacement(frame, ttl))

            additionalTags.foreach { tag =>
              newValue = setValue("as", tag, "tags", newValue)
            }

            val eventId = frame.eventIdOrNA

            Cherrypicked >>> Seq(
              'From -> sourcePath,
              'Result -> newValue,
              'KeepOriginal -> keepOriginalEvent,
              'EventId -> eventId,
              'NewEventId -> newEventId,
              'InstructionInstanceId -> uuid)

            result :+ newValue
          case None => result
        }

      }
    }


}

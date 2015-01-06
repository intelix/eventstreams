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

trait CherrypickInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val Cherrypicked = 'Cherrypicked.trace

  override def componentId: String = "Instruction.Cherrypick"
}

trait CherrypickInstructionConstants extends InstructionConstants with CherrypickInstructionEvents {
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

class CherrypickInstruction extends SimpleInstructionBuilder with CherrypickInstructionConstants with WithEventPublisher {
  val configId = "cherrypick"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFFieldName \/> Fail(s"Invalid $configId instruction. Missing '$CfgFFieldName' value. Contents: ${Json.stringify(props)}");
      valuePath <- props ~> CfgFFieldValuePath \/> Fail(s"Invalid $configId instruction. Missing '$CfgFFieldValuePath' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val eventIdTemplate = props ~> CfgFEventIdTemplate | "${eventId}_picked"
      val eventSeqTemplate = props ~> CfgFEventSeqTemplate | "${eventSeq}"
      val keepOriginalEvent = props ?> CfgFKeepOriginal | true
      val additionalTags = (props ~> CfgFAdditionalTags | "").split(",").map(_.trim)

      val index = props ~> CfgFIndex | "${index}"
      val table = props ~> CfgFTable | "${table}"
      val ttl = props ~> CfgFTTL | "${_ttl}"

      val uuid = Utils.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: JsonFrame => {

        val sourcePath = toPath(macroReplacement(frame, valuePath))
        val targetPath = toPath(macroReplacement(frame, fieldName))

        val result = if (keepOriginalEvent) List(frame) else List()

        frame.event.getOpt(sourcePath) match {
          case Some(v) =>
            var newValue = Json.obj().set(
              targetPath -> v
            )

            val newEventId = macroReplacement(frame, eventIdTemplate)

            newValue = setValue("s", JsString(newEventId), __ \ 'eventId, newValue)
            newValue = setValue("n", JsString(macroReplacement(frame, eventSeqTemplate)), __ \ 'eventSeq, newValue)
            newValue = setValue("s", JsString(macroReplacement(frame, index)), __ \ 'index, newValue)
            newValue = setValue("s", JsString(macroReplacement(frame, table)), __ \ 'table, newValue)
            newValue = setValue("s", JsString(macroReplacement(frame, ttl)), __ \ '_ttl, newValue)

            additionalTags.foreach { tag =>
              newValue = setValue("as", JsString(tag), __ \ 'tags, newValue)
            }

            val newFrame = frame.copy(event = newValue)

            val eventId = frame.event ~> 'eventId | "n/a"

            Cherrypicked >>> Seq(
              'From -> sourcePath,
              'Result -> Json.stringify(newValue),
              'KeepOriginal -> keepOriginalEvent,
              'EventId -> eventId,
              'NewEventId -> newEventId,
              'InstructionInstanceId -> uuid)

            result :+ newFrame
          case None => result
        }

      }
    }


}

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

package hq.flows.core

import agent.controller.flow.Tools._
import common.ToolExt.configHelper
import common.{Fail, JsonFrame}
import hq.flows.core.Builder.SimpleInstructionType
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

class CherrypickInstruction extends SimpleInstructionBuilder {
  val configId = "cherrypick"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> 'fieldName \/> Fail(s"Invalid cherrypick instruction. Missing 'fieldName' value. Contents: ${Json.stringify(props)}");
      valuePath <- props ~> 'fieldValuePath \/> Fail(s"Invalid cherrypick instruction. Missing 'fieldValuePath' value. Contents: ${Json.stringify(props)}")
    ) yield {
      val fieldValue = props #> 'fieldValue | JsString("")

      val eventIdTemplate = props ~> 'eventIdTemplate | "${eventId}_picked"
      val eventSeqTemplate = props ~> 'eventSeqTemplate | "${eventSeq}"
      val keepOriginalEvent = props ?> 'keepOriginalEvent | true
      val additionalTags = (props ~> 'additionalTags | "").split(",").map(_.trim)

      val index = props ~> 'index | "${index}"
      val table = props ~> 'table | "${table}"
      val ttl = props ~> 'ttl | "${_ttl}"

      frame: JsonFrame => {

        logger.debug(s"Original frame: $frame")

        val sourcePath = toPath(macroReplacement(frame, valuePath))
        val targetPath = toPath(macroReplacement(frame, fieldName))

        logger.debug("Source path: {}", sourcePath)
        logger.debug("Target path: {}", targetPath)

        val result = if (keepOriginalEvent) List(frame) else List()

        frame.event.getOpt(sourcePath) match  {
          case Some(v) =>
            logger.debug(s"Replacement: $v")
            var newValue = Json.obj().set(
              targetPath -> v
            )
            newValue = setValue("s",JsString(macroReplacement(frame, eventIdTemplate)),__ \ 'eventId, newValue)
            newValue = setValue("n",JsString(macroReplacement(frame, eventSeqTemplate)),__ \ 'eventSeq, newValue)
            newValue = setValue("s",JsString(macroReplacement(frame, index)),__ \ 'index, newValue)
            newValue = setValue("s",JsString(macroReplacement(frame, table)),__ \ 'table, newValue)
            newValue = setValue("s",JsString(macroReplacement(frame, ttl)),__ \ '_ttl, newValue)

            additionalTags.foreach { tag =>
              newValue = setValue("as",JsString(tag),__ \ 'tags, newValue)
            }

            val newFrame = frame.copy(event = newValue)
            logger.debug("New frame: {}", newFrame)
            result :+ newFrame
          case None => result
        }

      }
    }


}

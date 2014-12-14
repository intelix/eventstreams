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
import play.api.libs.json.{JsString, JsValue, Json}

import scalaz.Scalaz._
import scalaz._

class AddTagInstruction extends SimpleInstructionBuilder {
  val configId = "addtag"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      tagName <- props ~> 'tagName \/> Fail(s"Invalid addtag instruction. Missing 'tagName' value. Contents: ${Json.stringify(props)}")
    ) yield {

      frame: JsonFrame => {

        val fieldName = "tags"
        val fieldType = "as"

        logger.debug(s"Original frame: $frame")

        val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])

        logger.debug("Key path: {}", keyPath)

        val replacement: JsValue = macroReplacement(frame, JsString(tagName))

        logger.debug(s"Replacement: $replacement type $fieldType" )

        val value: JsValue = setValue(fieldType, replacement, keyPath, frame.event)

        logger.debug("New frame: {}", value)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

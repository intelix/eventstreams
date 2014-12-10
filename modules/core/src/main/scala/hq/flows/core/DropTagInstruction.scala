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

private[core] object DropTagInstruction extends SimpleInstructionBuilder {
  val configId = "droptag"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      tagName <- props ~> 'tagName \/> Fail(s"Invalid droptag instruction. Missing 'tagName' value. Contents: ${Json.stringify(props)}")
    ) yield {

      frame: JsonFrame => {

        val fieldName = "tags"
        val fieldType = "as"

        logger.debug(s"Original frame: $frame")

        val name = macroReplacement(frame, JsString(tagName)).asOpt[String].getOrElse("")

        val originalValue = locateFieldValue(frame, fieldName).asOpt[JsArray].getOrElse(Json.arr()).value

        val newValue = Json.toJson( originalValue.filter(_.asOpt[String].getOrElse("") != name).toArray )

        val value: JsValue = frame.event.set(toPath(fieldName) -> newValue)

        logger.debug("New frame: {}", value)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

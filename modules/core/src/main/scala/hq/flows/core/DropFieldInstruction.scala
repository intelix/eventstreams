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

private[core] object DropFieldInstruction extends SimpleInstructionBuilder {
  val configId = "dropfield"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> 'fieldName \/> Fail(s"Invalid dropfield instruction. Missing 'fieldName' value. Contents: ${Json.stringify(props)}")
    ) yield {

      frame: JsonFrame => {

        logger.debug(s"Original frame: $frame")

        val path = toPath(macroReplacement(frame, JsString(fieldName)).asOpt[String].getOrElse(""))

        logger.debug(s"Dropping: $fieldName  at $path")

        val value: JsValue = frame.event.delete(path)

        logger.debug("New frame: {}", value)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

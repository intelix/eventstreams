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

import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.SimpleInstructionBuilder
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

class DropFieldInstruction extends SimpleInstructionBuilder {
  val configId = "dropfield"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> 'fieldName \/> Fail(s"Invalid dropfield instruction. Missing 'fieldName' value. Contents: ${Json.stringify(props)}")
    ) yield {

      frame: JsonFrame => {

        logger.debug(s"Original frame: $frame")

        val field = macroReplacement(frame, fieldName)

        val value = if (field.startsWith("?")) {
          val actualFieldName = field.substring(1).toLowerCase
          frame.event.updateAll { case (p, js) if JsPathExtension.hasKey(p).map(_.toLowerCase == actualFieldName) | false =>
            logger.debug(s"Dropping: $actualFieldName  at $p")
            JsNull
          }
        } else {
          val path = toPath(macroReplacement(frame, JsString(field)).asOpt[String].getOrElse(""))
          logger.debug(s"Dropping: $fieldName  at $path")
          frame.event.getOpt(path).map { _ =>
            frame.event.set(path -> JsNull)
          } | frame.event
        }

        logger.debug("New frame: {}", value)

        List(JsonFrame(value, frame.ctx))

      }
    }


}

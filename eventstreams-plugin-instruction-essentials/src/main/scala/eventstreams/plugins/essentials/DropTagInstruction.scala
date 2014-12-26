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
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._


trait DropTagInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val TagDropped = 'TagDropped.trace

  override def componentId: String = "Instruction.DropTag"
}

trait DropTagInstructionConstants extends InstructionConstants with DropTagInstructionEvents {
  val CfgFTagToDrop = "tagToDrop"
}


class DropTagInstruction extends SimpleInstructionBuilder with DropTagInstructionConstants with WithEventPublisher {
  val configId = "droptag"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      tagName <- props ~> CfgFTagToDrop \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTagToDrop' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = Utils.generateShortUUID

      Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

      frame: JsonFrame => {

        val fieldName = "tags"
        val fieldType = "as"

        val name = macroReplacement(frame, JsString(tagName)).asOpt[String].getOrElse("")

        val originalValue = locateFieldValue(frame, fieldName).asOpt[JsArray].getOrElse(Json.arr()).value

        if (originalValue.exists(_.asOpt[String].contains(name))) {
          val newValue = Json.toJson(originalValue.filter(_.asOpt[String].getOrElse("") != name).toArray)

          val value: JsValue = frame.event.set(toPath(fieldName) -> newValue)

          val eventId = frame.event ~> 'eventId | "n/a"

          TagDropped >>('Tag --> name, 'EventId --> eventId, 'InstructionInstanceId --> uuid)

          List(JsonFrame(value, frame.ctx))
        } else List(frame)

      }
    }


}

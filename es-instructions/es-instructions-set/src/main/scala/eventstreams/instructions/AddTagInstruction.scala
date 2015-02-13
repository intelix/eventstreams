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
import eventstreams.JSONTools.{configHelper, _}
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._


trait AddTagInstructionSysevents
  extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val TagAdded = 'TagAdded.trace

  override def componentId: String = "Instruction.AddTag"
}

trait AddTagInstructionConstants extends InstructionConstants with AddTagInstructionSysevents  {
  val CfgFTagToAdd = "tagToAdd"
}

class AddTagInstruction extends SimpleInstructionBuilder with AddTagInstructionConstants with WithSyseventPublisher {
  val configId = "addtag"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      tagName <- props ~> CfgFTagToAdd \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTagToAdd' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = UUIDTools.generateShortUUID

      Built >> ('Tag ->  tagName, 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val fieldName = "tags"
        val fieldType = "as"

        val keyPath = macroReplacement(frame, fieldName)

        val replacement = macroReplacement(frame, tagName)

        val value: EventFrame = setValue(fieldType, replacement, keyPath, frame)

        val eventId = value.eventIdOrNA
        
        TagAdded >>('Tag -> tagName, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

        List(value)

      }
    }


}

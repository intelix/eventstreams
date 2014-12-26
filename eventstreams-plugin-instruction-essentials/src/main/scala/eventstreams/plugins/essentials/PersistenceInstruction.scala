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
import core.events.WithEvents
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

trait PersistenceInstructionEvents
  extends ComponentWithBaseEvents
  with WithEvents {

  val Built = 'Built.trace
  val Configured = 'Configured.trace

  override def id: String = "Instruction.PersistenceParams"
}

trait PersistenceInstructionConstants extends InstructionConstants with PersistenceInstructionEvents {
  val CfgFIndex = "index"
  val CfgFTable = "table"
  val CfgFTTL = "ttl"
}

object PersistenceInstructionConstants extends PersistenceInstructionConstants

class PersistenceInstruction extends SimpleInstructionBuilder with PersistenceInstructionConstants {
  val configId = "persistence"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      index <- props ~> CfgFIndex \/> Fail(s"Invalid $configId instruction. Missing '$CfgFIndex' value. Contents: ${Json.stringify(props)}");
      table <- props ~> CfgFTable \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTable' value. Contents: ${Json.stringify(props)}");
      ttl <- props ~> CfgFTTL \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTTL' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = Utils.generateShortUUID

      Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

      frame: JsonFrame => {

        val eventId = frame.event ~> 'eventId | "n/a"

        val indexValue = macroReplacement(frame, index)
        val tableValue = macroReplacement(frame, table)
        val ttlValue = macroReplacement(frame, ttl)

        Configured >>(
          'Index --> indexValue,
          'Table --> tableValue,
          'TTL --> ttlValue,
          'EventId --> eventId,
          'InstructionInstanceId --> uuid)

        List(JsonFrame(
          frame.event.set(
            __ \ 'index -> JsString(indexValue),
            __ \ 'table -> JsString(tableValue),
            __ \ '_ttl -> JsString(ttlValue)),
          frame.ctx))

      }
    }


}

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
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz._

trait PersistenceInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Configured = 'Configured.trace

  override def componentId: String = "Instruction.PersistenceParams"
}

trait PersistenceInstructionConstants extends InstructionConstants with PersistenceInstructionSysevents {
  val CfgFIndex = "index"
  val CfgFTable = "table"
  val CfgFTTL = "ttl"
}

object PersistenceInstructionConstants extends PersistenceInstructionConstants

class PersistenceInstruction extends SimpleInstructionBuilder with PersistenceInstructionConstants with WithSyseventPublisher {
  val configId = "persistence"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      index <- props ~> CfgFIndex \/> Fail(s"Invalid $configId instruction. Missing '$CfgFIndex' value. Contents: ${Json.stringify(props)}");
      table <- props ~> CfgFTable \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTable' value. Contents: ${Json.stringify(props)}");
      ttl <- props ~> CfgFTTL \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTTL' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = UUIDTools.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val eventId = frame.eventIdOrNA

        val indexValue = macroReplacement(frame, index)
        val tableValue = macroReplacement(frame, table)
        val ttlValue = macroReplacement(frame, ttl)

        Configured >>(
          'Index -> indexValue,
          'Table -> tableValue,
          'TTL -> ttlValue,
          'EventId -> eventId,
          'InstructionInstanceId -> uuid)

        List(frame + ('index -> indexValue) + ('table -> tableValue) + ('_ttl -> ttlValue))

      }
    }


}

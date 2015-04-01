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
import eventstreams.Tools.{configHelper, _}
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import play.api.libs.json.{JsValue, Json}

import scalaz._


trait BulkEnrichInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Enriched = 'Enriched.trace

  override def componentId: String = "Instruction.BulkEnrich"
}

trait BulkEnrichInstructionConstants extends InstructionConstants {
  val CfgFEnrichmentRules = "enrichmentRules"
}

class BulkEnrichInstruction extends SimpleInstructionBuilder with BulkEnrichInstructionConstants with BulkEnrichInstructionSysevents with WithSyseventPublisher {
  val configId = "bulkenrich"

  private val RulePattern = "([^|]+)[|](.)=(.+)".r

  case class Rule(field: String, valueType: String, valueTemplate: String)





  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      enrichmentRules <- props ~> CfgFEnrichmentRules orFail s"Invalid $configId instruction. Missing '$CfgFEnrichmentRules' value. Contents: ${Json.stringify(props)}"
    ) yield {

      val rules = enrichmentRules.split('\n').map(_.trim).filterNot(_.isEmpty).collect {
        case RulePattern(f,ty,tpl) => Rule(f,ty,tpl)
      }

      val uuid = UUIDTools.generateShortUUID

      Built >>('Rules -> rules.map { r => s"${r.field}|${r.valueType}=${r.valueTemplate}" }, 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val eventId = frame.eventIdOrNA

        val value = rules.foldLeft[EventFrame](frame) {
          case (f, Rule(n,s,v)) =>
            val keyPath = macroReplacement(f, n)

            val replacement: String = macroReplacement(f, v)

            val interim = setValue(s, replacement, keyPath, f)
            Enriched >>('Path -> keyPath, 'Replacement -> replacement, 'NewValue -> interim, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

            interim
        }



        List(value)

      }
    }


}

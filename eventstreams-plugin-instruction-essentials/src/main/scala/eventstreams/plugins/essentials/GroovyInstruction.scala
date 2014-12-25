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
import eventstreams.core.Tools.configHelper
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import eventstreams.core.{Fail, JsonFrame, Utils}
import groovy.json.{JsonBuilder, JsonSlurper}
import groovy.lang.{Binding, GroovyShell}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

trait GroovyInstructionEvents
  extends ComponentWithBaseEvents
  with WithEvents {

  val Built = 'Built.trace
  val GroovyExecOk = 'GroovyExecOk.trace
  val GroovyExecResult = 'GroovyExecResult.trace
  val GroovyExecFailed = 'GroovyExecFailed.error

  override def id: String = "Instruction.Groovy"
}

trait GroovyInstructionConstants extends InstructionConstants with GroovyInstructionEvents {
  val CfgFCode = "code"

  val CtxJsonBuilder = "json"
}

object GroovyInstructionConstants extends GroovyInstructionConstants

class GroovyInstruction extends SimpleInstructionBuilder with GroovyInstructionConstants {
  val configId = "groovy"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      code <- props ~> CfgFCode \/> Fail(s"Invalid $configId instruction. Missing '$CfgFCode' value. Contents: ${Json.stringify(props)}")
    ) yield {

      val uuid = Utils.generateShortUUID

      Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

      var binding = new Binding()
      var shell = new GroovyShell(binding)

      fr: JsonFrame =>

        val text = new JsonSlurper().parseText(Json.stringify(fr.event))

        binding.setVariable(CtxJsonBuilder, new JsonBuilder(text))

        val eventId = fr.event ~> 'eventId | "n/a"

        var result = try {
          var value = shell.evaluate(code) match {
            case x: JsonBuilder => x.toPrettyString
            case x: String => x
          }

          GroovyExecOk >>('EventId --> eventId, 'InstructionInstanceId --> uuid)
          GroovyExecResult >>('Result --> value, 'EventId --> eventId, 'InstructionInstanceId --> uuid)

          Json.parse(value)
        } catch {
          case x: Throwable =>
            GroovyExecFailed >>('Error --> x.getMessage, 'EventId --> eventId, 'InstructionInstanceId --> uuid)
            fr.event.set(__ \ 'error -> JsString("Groovy instruction failed: " + x.getMessage))
        }

        List(fr.copy(event = result))
    }


}

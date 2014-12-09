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

import common.ToolExt.configHelper
import common.{Fail, JsonFrame}
import groovy.json.JsonSlurper
import groovy.lang.{Binding, GroovyShell}
import hq.flows.core.Builder.SimpleInstructionType
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

private[core] object GroovyInstruction extends SimpleInstructionBuilder {
  val configId = "groovy"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      code <- props ~> 'code \/> Fail(s"Invalid groovy instruction. Missing 'code' value. Contents: ${Json.stringify(props)}")
    ) yield {

      fr: JsonFrame =>

        var binding = new Binding()
        binding.setVariable("foo", new Integer(2))
        var shell = new GroovyShell(binding)

        binding.setVariable("jsonParser", new JsonSlurper())
        binding.setVariable("event", new JsonSlurper().parseText(Json.stringify(fr.event)))

        binding.setVariable("func", (term: String) => {
          "result"
        })

        var result = try {
          var value = String.valueOf(shell.evaluate(code))
          Json.parse(value)
        }
        catch {
          case x: Throwable => fr.event.set(__ \ 'error -> JsString("Groovy instruction failed: " + x.getMessage))
        }

        List(fr.copy(event = result))
    }


}

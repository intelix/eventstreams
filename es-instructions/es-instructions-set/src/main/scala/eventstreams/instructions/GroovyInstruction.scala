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

import java.util

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.instructions.Types.SimpleInstructionType
import groovy.json.{JsonBuilder, JsonSlurper}
import groovy.lang.{Binding, GroovyShell}
import play.api.libs.json._

import scala.collection.JavaConverters._
import scala.util.Try
import scalaz.Scalaz._
import scalaz._

trait GroovyInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val GroovyExecOk = 'GroovyExecOk.trace
  val GroovyExecResult = 'GroovyExecResult.trace
  val GroovyExecFailed = 'GroovyExecFailed.error

  override def componentId: String = "Instruction.Groovy"
}

trait GroovyInstructionConstants extends InstructionConstants with GroovyInstructionSysevents {
  val CfgFCode = "code"

  val CtxJsonBuilder = "json"
  val CtxContext = "ctx"
  val CtxCopyEvent = "copyJson"
  val CtxNewEvent = "newJson"
}

object GroovyInstructionConstants extends GroovyInstructionConstants

class GroovyInstruction extends SimpleInstructionBuilder with GroovyInstructionConstants with WithSyseventPublisher {
  val configId = "groovy"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      code <- props ~> CfgFCode \/> Fail(s"Invalid $configId instruction. Missing '$CfgFCode' value. Contents: ${Json.stringify(props)}");
      script <- Try {
        new GroovyShell().parse(code).right
      }.recover {
        case t => -\/(Fail(s"Invalid $configId instruction. Invalid '$CfgFCode'. Contents: $props"))
      }.get
    ) yield {

      val uuid = UUIDTools.generateShortUUID

      Built >> ('InstructionInstanceId -> uuid)

      var binding = new Binding()
      var shell = new GroovyShell(binding)
      var ctx = new util.HashMap()
      binding.setVariable(CtxContext, ctx)

      def copyJ() = new JsonBuilder(new JsonSlurper().parseText(binding.getVariable(CtxJsonBuilder).asInstanceOf[JsonBuilder].toPrettyString))
      def newJ() = new JsonBuilder(new JsonSlurper().parseText("{}"))

      binding.setVariable(CtxCopyEvent, copyJ _)

      binding.setVariable(CtxNewEvent, newJ _)

      script.setBinding(binding)

      fr: EventFrame =>

        val text = new JsonSlurper().parseText(Json.stringify(fr.asJson))

        binding.setVariable(CtxJsonBuilder, new JsonBuilder(text))

        val eventId = fr.eventIdOrNA

        try {

          var value = script.run() match {
            case x: JsonBuilder => List(x.toPrettyString)
            case x: JsonSlurper => List(new JsonBuilder(x).toPrettyString)
            case x: String => List(x)
            case b: Array[JsonBuilder] => b.map(_.toPrettyString).toList
            case b: util.List[_] => b.asInstanceOf[util.List[JsonBuilder]].asScala.map(_.toPrettyString).toList

          }

          GroovyExecOk >>('EventId -> eventId, 'InstructionInstanceId -> uuid)

          val parentId = fr.eventId | UUIDTools.generateShortUUID
          var counter = 0
          value.map { next =>
            counter = counter + 1
            val j = EventFrameConverter.fromJson(Json.parse(next))
            val enriched = j.eventId match {
              case Some(x) => j
              case None =>
                j + ('eventId -> (parentId + ":" + counter))
            }
            GroovyExecResult >>('Result -> value, 'EventId -> (enriched ~> 'eventId), 'InstructionInstanceId -> uuid)
            enriched
          }
        } catch {
          case x: Throwable =>
            GroovyExecFailed >>('Error -> x.getMessage, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
            List(fr + ('error -> ("Groovy instruction failed: " + x.getMessage)))
        }

    }


}

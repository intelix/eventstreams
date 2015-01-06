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

package eventstreams.plugins.essentials

import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


trait GrokInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val Grokked = 'Grokked.trace

  override def componentId: String = "Instruction.Grok"
}

trait GrokInstructionConstants extends InstructionConstants with GrokInstructionEvents {
  val CfgFPattern = "pattern"
  val CfgFSource = "source"
  val CfgFFields = "fields"
  val CfgFTypes = "types"
  val CfgFGroups = "groups"
  val CfgFValues = "values"
}

object GrokInstructionConstants extends GrokInstructionConstants

class GrokInstruction extends SimpleInstructionBuilder with GrokInstructionConstants with WithEventPublisher {
  val configId = "grok"

  def nextField(eventId: String, uuid: String)(frame: JsonFrame, fvt: ((String, String), String)): JsonFrame =
    fvt match {
      case ((f, v), t) =>
        val field = toPath(macroReplacement(frame, JsString(f)))
        val value = macroReplacement(frame, JsString(v))
        Grokked >>('Field -> field, 'Value -> value, 'Type -> t, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
        JsonFrame(setValue(t, value, field, frame.event), frame.ctx)
    }

  def populateContext(frame: JsonFrame, gv: (String, String)): JsonFrame =
    gv match {
      case (g, v) =>
        JsonFrame(frame.event, frame.ctx + (g -> JsString(v)))
    }


  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      pattern <- props ~> CfgFPattern \/> Fail(s"Invalid $configId instruction. Missing '$CfgFPattern' value. Contents: ${Json.stringify(props)}");
      regex = new Regex(pattern);

      source <- props ~> CfgFSource \/> Fail(s"Invalid $configId instruction. Missing '$CfgFSource' value. Contents: ${Json.stringify(props)}");

      fieldsList <- props ~> CfgFFields \/> Fail(s"Invalid $configId instruction. Missing '$CfgFFields' value. Contents: ${Json.stringify(props)}");
      fields = fieldsList.split(',').map(_.trim);

      typesList <- props ~> CfgFTypes \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTypes' value. Contents: ${Json.stringify(props)}");
      types = typesList.split(',').map(_.trim);

      groupsList <- props ~> CfgFGroups \/> Fail(s"Invalid $configId instruction. Missing '$CfgFGroups' value. Contents: ${Json.stringify(props)}");
      groups = groupsList.split(',').map(_.trim);

      valuesList <- props ~> CfgFValues \/> Fail(s"Invalid $configId instruction. Missing '$CfgFValues' value. Contents: ${Json.stringify(props)}");
      values = valuesList.split(',').map(_.trim)
    ) yield {

      val uuid = Utils.generateShortUUID

      def contextToFrame(frame: JsonFrame, rmatch: Regex.Match): JsonFrame =
        (groups zip rmatch.subgroups).foldLeft(frame)(populateContext)


      def matcherToFrame(eventId: String)(frame: JsonFrame, rmatch: Regex.Match): JsonFrame =
        (fields zip values zip types)
          .foldLeft(contextToFrame(frame, rmatch))(nextField(eventId, uuid))

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      fr: JsonFrame =>

        val sourceValue = locateFieldValue(fr, macroReplacement(fr, JsString(source)))
        val eventId = fr.event ~> 'eventId | "n/a"

        List(regex.findAllMatchIn(sourceValue)
          .toSeq
          .foldLeft(fr)(matcherToFrame(eventId)))
    }


}

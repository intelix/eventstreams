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
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


trait GrokInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val Grokked = 'Grokked.trace

  override def componentId: String = "Instruction.Grok"
}

trait GrokInstructionConstants extends InstructionConstants with GrokInstructionSysevents {
  val CfgFPattern = "pattern"
  val CfgFSource = "source"
  val CfgFFields = "fields"
  val CfgFTypes = "types"
  val CfgFGroups = "groups"
  val CfgFValues = "values"
}

object GrokInstructionConstants extends GrokInstructionConstants

class GrokInstruction extends SimpleInstructionBuilder with GrokInstructionConstants with WithSyseventPublisher {
  val configId = "grok"

  def nextField(eventId: String, uuid: String, ctx: Map[String,String])(frame: EventFrame, fvt: ((String, String), String)): EventFrame =
    fvt match {
      case ((f, v), t) =>
        val field = macroReplacement(frame, Some(ctx), f)
        val value = macroReplacement(frame, Some(ctx), v)
        Grokked >>('Field -> field, 'Value -> value, 'Type -> t, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
        setValue(t, value, field, frame)
    }

  def populateContext(ctx: Map[String,String], gv: (String, String)): Map[String,String] =
    gv match {
      case (g, v) =>
        ctx + (g -> v)
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

      val uuid = UUIDTools.generateShortUUID

      def contextForFrame(rmatch: Regex.Match): Map[String,String] =
        (groups zip rmatch.subgroups).foldLeft(Map[String,String]())(populateContext)


      def matcherToFrame(eventId: String)(frame: EventFrame, rmatch: Regex.Match): EventFrame =
        (fields zip values zip types)
          .foldLeft(frame)(nextField(eventId, uuid, contextForFrame(rmatch)))

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      fr: EventFrame =>

        val sourceValue = locateFieldValue(fr, macroReplacement(fr, JsString(source)))
        val eventId = fr.eventIdOrNA

        List(regex.findAllMatchIn(sourceValue)
          .toSeq
          .foldLeft(fr)(matcherToFrame(eventId)))
    }


}

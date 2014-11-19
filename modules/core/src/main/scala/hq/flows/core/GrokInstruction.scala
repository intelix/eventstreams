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

import agent.controller.flow.Tools._
import com.typesafe.scalalogging.StrictLogging
import common.{JsonFrame, Fail}
import common.ToolExt.configHelper
import hq.flows.core.Builder.{SimpleInstructionType, InstructionType}
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._

private[core] object GrokInstruction extends SimpleInstructionBuilder {
  val configId = "grok"

  override def simpleInstruction(props: JsValue): \/[Fail, SimpleInstructionType] =
    for (
      pattern <- props ~> 'pattern \/> Fail(s"Invalid grok instruction. Missing 'pattern' value. Contents: ${Json.stringify(props)}");
      regex = new Regex(pattern);

      source <- props ~> 'source \/> Fail(s"Invalid grok instruction. Missing 'source' value. Contents: ${Json.stringify(props)}");

      fieldsList <- props ~> 'fields \/> Fail(s"Invalid grok instruction. Missing 'fields' value. Contents: ${Json.stringify(props)}");
      fields = fieldsList.split(',').map(_.trim);

      typesList <- props ~> 'types \/> Fail(s"Invalid grok instruction. Missing 'types' value. Contents: ${Json.stringify(props)}");
      types = typesList.split(',').map(_.trim);

      groupsList <- props ~> 'groups \/> Fail(s"Invalid grok instruction. Missing 'groups' value. Contents: ${Json.stringify(props)}");
      groups = groupsList.split(',').map(_.trim);

      valuesList <- props ~> 'values \/> Fail(s"Invalid grok instruction. Missing 'values' value. Contents: ${Json.stringify(props)}");
      values = valuesList.split(',').map(_.trim)
    ) yield {

      def nextField(frame: JsonFrame, fvt: ((String, String), String)): JsonFrame =
        fvt match {
          case ((f, v), t) =>
            logger.debug(s"next field: $f = $v of $t")
            JsonFrame(setValue(t, macroReplacement(frame, JsString(v)), toPath(macroReplacement(frame, JsString(f))), frame.event), frame.ctx)
        }

      def populateContext(frame: JsonFrame, gv: (String, String)): JsonFrame =
        gv match {
          case (g, v) =>
            logger.debug(s"adding to context: $g = $v")
            JsonFrame(frame.event, frame.ctx + (g -> JsString(v)))
        }

      def contextToFrame(frame: JsonFrame, rmatch: Regex.Match): JsonFrame =
        (groups zip rmatch.subgroups).foldLeft(frame)(populateContext)


      def matcherToFrame(frame: JsonFrame, rmatch: Regex.Match): JsonFrame =
        (fields zip values zip types)
          .foldLeft(contextToFrame(frame, rmatch))(nextField)


      fr: JsonFrame =>

        val sourceValue = locateFieldValue(fr, macroReplacement(fr, JsString(source)))

        List(regex.findAllMatchIn(sourceValue)
          .toSeq
          .foldLeft(fr)(matcherToFrame))
    }


}

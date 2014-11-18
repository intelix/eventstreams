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
import common.ToolExt.configHelper
import common.{Fail, JsonFrame}
import hq.flows.core.Builder.InstructionType
import play.api.libs.json.{JsString, JsValue, Json}

import scala.annotation.tailrec
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._

private[core] object SplitProcessorBuilder extends BuilderFromConfig[InstructionType] with StrictLogging {
  val configId = "split"

  override def build(props: JsValue): \/[Fail, InstructionType] =
    for (
      id <- props ~> 'id \/> Fail(s"Invalid split instruction. Missing 'id' value. Contents: ${Json.stringify(props)}");
      source <- props ~> 'source \/> Fail(s"Invalid split instruction. Missing 'source' value. Contents: ${Json.stringify(props)}");
      pattern <- (props ~> 'pattern).map(new Regex(_)) \/> Fail(s"Invalid split instruction. Missing 'pattern' value. Contents: ${Json.stringify(props)}")
    ) yield {

      var remainder : Option[String] = None

      @tailrec
      def ext(list: List[String], remainder: Option[String]) : (List[String], Option[String]) =
        remainder.flatMap(pattern.findFirstMatchIn(_)) match {
          case None => (list, remainder)
          case Some(m) => ext(list :+ m.group(1), Some(m.group(2)))
        }

      fr: JsonFrame => {

        val sourceField = macroReplacement(fr, JsString(source))

        val (resultList, newRemainder) = ext(List(), Some(remainder.getOrElse("") + locateFieldValue(fr, sourceField).asOpt[String].getOrElse("")))

        remainder = newRemainder

        logger.debug(s"After split, events: ${resultList.size}, remainder: $newRemainder")

        resultList.map { value =>
          JsonFrame(setValue("s", JsString(value), toPath(sourceField), fr.event), fr.ctx)
        }

      }
    }


}
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
import common.ToolExt.configHelper
import common.{Fail, JsonFrame}
import hq.flows.core.Builder.SimpleInstructionType
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

class IntervalCalcInstruction extends SimpleInstructionBuilder {
  val configId = "intervalcalc"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> 'intervalFieldName \/> Fail(s"Invalid intervalcalc instruction. Missing 'intervalFieldName' value. Contents: ${Json.stringify(props)}")
    ) yield {

          var last: Option[Long] = None

      frame: JsonFrame => {


        val v = frame.event +> 'date_ts | 0

        if (v == 0) {
          last = None
          logger.debug("date_ts field is not available, skipping")
          List(frame)
        } else {
          last match {
            case Some(x) =>
              val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])
              val interval = v - x
              last = Some(v)
              List(frame.copy(event = frame.event.set(keyPath -> JsNumber(interval))))
            case None =>
              last = Some(v)
              List(frame)
          }
        }

      }
    }


}

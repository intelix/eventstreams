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

import com.typesafe.scalalogging.StrictLogging
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.instructions.Types._
import play.api.libs.json.JsValue

import scalaz._
import Scalaz._


trait SimpleInstructionBuilder extends BuilderFromConfig[InstructionType] with StrictLogging {
  def maxInFlight = 1000

  def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType]

  def wrapInCondition(instr: SimpleInstructionType, maybeCondition: Option[Condition]): SimpleInstructionType =
    maybeCondition match {
      case None => instr
      case Some(cond) => fr: EventFrame => cond.metFor(fr) match {
        case -\/(fail) =>
          List(fr)
        case \/-(_) => instr(fr)
      }
    }

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    simpleInstruction(props, id).map { instr =>
      SimpleInstructionWrappingActor.props(
        (wrapInCondition(instr, SimpleCondition.conditionOrAlwaysTrue(props ~> 'simpleCondition)), None),
        maxInFlight, id|"N/A")
    }
}

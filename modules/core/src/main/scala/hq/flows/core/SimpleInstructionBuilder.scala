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

import com.typesafe.scalalogging.StrictLogging
import common.{JsonFrame, Fail}
import hq.flows.SimpleInstructionWrappingActor
import hq.flows.core.Builder._
import play.api.libs.json.JsValue

import scalaz.{\/-, -\/, \/}


trait SimpleInstructionBuilder extends BuilderFromConfig[InstructionType] with StrictLogging {
  def maxInFlight = 16

  def simpleInstruction(props: JsValue): \/[Fail, SimpleInstructionType]

  def wrapInCondition(instr: SimpleInstructionType, maybeCondition: Option[Condition]) : SimpleInstructionType =
    maybeCondition match {
      case None => instr
      case Some(cond) => fr: JsonFrame => cond.metFor(fr) match {
        case -\/(fail) =>
          logger.debug("Condition failed: " + fail)
          List(fr)
        case \/-(_) => instr(fr)
      }
    }

  override def build(props: JsValue, maybeCondition: Option[Condition]): \/[Fail, InstructionType] =
    simpleInstruction(props).map { instr => SimpleInstructionWrappingActor.props(wrapInCondition(instr, maybeCondition), maxInFlight) }
}
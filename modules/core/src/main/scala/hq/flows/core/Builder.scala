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

import akka.actor.{ActorRefFactory, Props}
import com.typesafe.scalalogging.StrictLogging
import common.ToolExt._
import common.{Fail, JsonFrame}
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz.{\/, \/-}

case class FlowComponents(tap: Props, pipeline: Seq[Props], sink: Props)

object Builder extends StrictLogging {

  type TapActorPropsType = Props
  type SinkActorPropsType = Props
  type InstructionType = Props
  type SimpleInstructionType = (JsonFrame) => scala.collection.immutable.Seq[JsonFrame]


  def apply()(implicit config: JsValue, f: ActorRefFactory): \/[Fail, FlowComponents] =
    for (
      tap <- buildTap;
      pipeline <- buildProcessingPipeline
    ) yield FlowComponents(tap, pipeline, BlackholeAutoAckSinkActor.props)


  def buildTap(implicit config: JsValue, f: ActorRefFactory): \/[Fail, TapActorPropsType] = {
    val allBuilders = Seq(GateInputBuilder, StatsdInputBuilder)
    for (
      input <- config #> 'tap \/> Fail("Invalid config: missing 'tap' branch");
      inputClass <- input ~> 'class \/> Fail("Invalid input config: missing 'class' value");
      builder <- allBuilders.find(_.configId == inputClass)
        \/> Fail(s"Unsupported or invalid input class $inputClass. Supported classes: ${allBuilders.map(_.configId)}");
      tap <- builder.build(input, None)
    ) yield tap

  }

  def buildInstruction(implicit config: JsValue): \/[Fail, InstructionType] = {
    val allBuilders = Seq(
      EnrichInstruction,
      GrokInstruction,
      GroovyInstruction,
      LogInstruction,
      DropInstruction,
      SplitInstruction,
      DateInstruction,
      GateInstruction,
      ElasticsearchInstruction,
      InfluxInstruction
    )
    for (
      instClass <- config ~> 'class \/> Fail("Invalid instruction config: missing 'class' value");
      builder <- allBuilders.find(_.configId == instClass)
        \/> Fail(s"Unsupported or invalid instruction class $instClass. Supported classes: ${allBuilders.map(_.configId)}");
      condition <- SimpleCondition(config ~> 'simpleCondition) | Condition(config #> 'condition);
      instr <- builder.build(config, Some(condition))
    ) yield instr
  }

  def buildProcessingPipeline(implicit config: JsValue): \/[Fail, Seq[InstructionType]] =
    for (
      instructions <- config ##> 'pipeline \/> Fail("Invalid pipeline config: missing 'pipeline' value");
      folded <- instructions.foldLeft[\/[Fail, Seq[InstructionType]]](\/-(List())) { (agg, next) =>
        for (
          list <- agg;
          instr <- buildInstruction(next)
        ) yield list :+ instr
      }
    ) yield folded


}

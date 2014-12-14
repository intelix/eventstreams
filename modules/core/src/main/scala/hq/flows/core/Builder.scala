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
import com.typesafe.config.Config
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
  type SimpleInstructionTypeWithGenerator = ((JsonFrame) => scala.collection.immutable.Seq[JsonFrame], Option[(Long) => scala.collection.immutable.Seq[JsonFrame]])


  def apply(implicit instructions: List[Config], config: JsValue, f: ActorRefFactory, id: String): \/[Fail, FlowComponents] =
    for (
      tap <- buildTap;
      pipeline <- buildProcessingPipeline
    ) yield FlowComponents(tap, pipeline, BlackholeAutoAckSinkActor.props(Some(id)))


  def buildTap(implicit config: JsValue, f: ActorRefFactory, id: String): \/[Fail, TapActorPropsType] =
    GateInputBuilder.build(config, None, Some(id))


  def buildInstruction(implicit instructionConfigs: List[Config], config: JsValue, id: String): \/[Fail, InstructionType] = {

    val allBuilders = instructionConfigs.map { cfg =>
      Class.forName(cfg.getString("class")).newInstance().asInstanceOf[BuilderFromConfig[InstructionType]]
    }

//    val allBuilders = Seq(
//      AddTagInstruction,
//      EnrichInstruction,
//      ReplaceInstruction,
//      GrokInstruction,
//      GroovyInstruction,
//      LogInstruction,
//      DropFieldInstruction,
//      DropTagInstruction,
//      DropInstruction,
//      SplitInstruction,
//      DateInstruction,
//      GateInstruction
////      ElasticsearchInstruction,
////      InfluxInstruction
//    )
    for (
      instClass <- config ~> 'class \/> Fail("Invalid instruction config: missing 'class' value");
      builder <- allBuilders.find(_.configId == instClass)
        \/> Fail(s"Unsupported or invalid instruction class $instClass. Supported classes: ${allBuilders.map(_.configId)}");
      instr <- builder.build(config, None, Some(id))
    ) yield instr
  }

  def buildProcessingPipeline(implicit instructionConfigs: List[Config], config: JsValue, id: String): \/[Fail, Seq[InstructionType]] =
    for (
      instructions <- config ##> 'pipeline \/> Fail("Invalid pipeline config: missing 'pipeline' value");
      folded <- instructions.foldLeft[\/[Fail, Seq[InstructionType]]](\/-(List())) { (agg, next) =>
        for (
          list <- agg;
          instr <- buildInstruction(instructionConfigs, next, id)
        ) yield list :+ instr
      }
    ) yield folded


}

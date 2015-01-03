package eventstreams.instructions

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

import _root_.core.events.EventOps.symbolToEventField
import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class ReplaceInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with ReplaceInstructionConstants {
    override def builder: SimpleInstructionBuilder = new ReplaceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFFieldName -> "value",
      CfgFPattern -> "\\d+",
      CfgFReplacementValue -> "${replacement}"
    )
  }
  
  import eventstreams.plugins.essentials.ReplaceInstructionConstants._

  s"ReplaceInstruction" should s"not build without $CfgFFieldName" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new ReplaceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFPattern -> "\\d+",
      CfgFReplacementValue -> "${replacement}"
    )

    shouldNotBuild()
  }

  it should s"not build without $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new ReplaceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFFieldName -> "value",
      CfgFReplacementValue -> "${replacement}"
    )

    shouldNotBuild()
  }

  it should s"not build with invalid $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new ReplaceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFFieldName -> "value",
      CfgFPattern -> "(",
      CfgFReplacementValue -> "${replacement}"
    )

    shouldNotBuild()
  }

  it should s"be built without $CfgFReplacementValue" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new ReplaceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFFieldName -> "value",
      CfgFPattern -> "\\d+"
    )

    shouldBuild()
  }

  it should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "replace value as configured" in new WithBasicConfig {
    expectOne(Json.obj("eventId" -> "id", "value" -> "bla1234bla345", "replacement" -> "REPL")) { result =>
      result ~> 'value should be (Some("blaREPLblaREPL"))
    }
  }

  it should "raise event when replaced" in new WithBasicConfig {
    expectEvent(Json.obj("eventId" -> "id", "value" -> "bla1234bla345", "replacement" -> "REPL"))(Replaced, 'NewValue --> "blaREPLblaREPL")
  }


}

package eventstreams

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
import eventstreams.plugins.essentials.{GrokInstructionConstants, GroovyInstruction, GroovyInstructionConstants}
import eventstreams.support.TestHelpers
import play.api.libs.json._

class GroovyInstructionTest extends TestHelpers {


  trait WithMinimalConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """json.content.gc.calculated=json.content.gc.totalafter-json.content.gc.newafter
          |json.content.gc.calculated2=json.content.gc.real == 0 ? 0 : (json.content.gc.user+json.content.gc.sys)/json.content.gc.real
          |json""".stripMargin
    )
  }

  s"GroovyInstruction with simple config" should s"not build without required fields (no ${GroovyInstructionConstants.CfgFCode}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "groovy")

    shouldNotBuild()
  }

  it should "build with valid config" in new WithMinimalConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithMinimalConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  val input = Json.obj("gc" -> Json.obj(
    "totalafter" -> 1000000,
    "newafter" -> 100000,
    "real" -> 3.1,
    "sys" -> 0.1,
    "user" -> 9.2
  ))

  "GroovyInstruction with valid config" should "raise events" in new WithMinimalConfig {
    expectEvent(input)(GroovyExecOk)
    expectEvent(input)(GroovyExecResult)
  }
  it should "produce correct json" in new WithMinimalConfig {
    expectOne(input) { result =>
      result #> 'gc +> 'calculated should be(Some(1000000 - 100000))
      result #> 'gc +> 'calculated2 should be(Some(3))
    }
  }
  it should "produce correct json, consistently" in new WithMinimalConfig {

    (1 to 1000) foreach { i =>
      val input = Json.obj("gc" -> Json.obj(
        "totalafter" -> (1000000 + i),
        "newafter" -> 100000,
        "real" -> 3.1,
        "sys" -> 0.1,
        "user" -> 9.2
      ))
      expectOne(input) { result =>
        result #> 'gc +> 'calculated should be(Some(1000000 + i - 100000))
        result #> 'gc +> 'calculated2 should be(Some(3))
      }

    }
  }

  trait WithInvalidConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """json.content.gc.calculated=json.content.gc.totalafter- bla bla
          |json.content.gc.calculated2=json.content.gc.real == 0 ? 0 : (json.content.gc.user+json.content.gc.sys)/json.content.gc.real
          |json""".stripMargin
    )
  }

  "GroovyInstruction with invalid config" should "raise events" in new WithInvalidConfig {
    expectEvent(input)(GroovyExecFailed)
  }
  it should "not modify original json" in new WithInvalidConfig {
    expectOne(input) { result =>
      result #> 'gc +&> 'user should be(Some(9.2))
    }
  }


  trait WithDangerousConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """json.content.gc.calculated2=(json.content.gc.user+json.content.gc.sys)/json.content.gc.real
          |json""".stripMargin
    )
  }

  val inputWithPoison = Json.obj("gc" -> Json.obj(
    "real" -> 0,
    "sys" -> 0.1,
    "user" -> 9.2
  ))

  "GroovyInstruction with dangerous config and poison input" should "raise events" in new WithDangerousConfig {
    expectEvent(inputWithPoison)(GroovyExecFailed, 'Error --> "Division by zero")
  }
  it should "not modify original json" in new WithDangerousConfig {
    expectOne(inputWithPoison) { result =>
      result #> 'gc +&> 'user should be(Some(9.2))
    }
  }


}

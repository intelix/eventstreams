package eventstreams

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

import eventstreams.core.EventFrame
import eventstreams.core.EventFrameConverter.optionsConverter
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
    expectEvent(EventFrame("abc1" -> "bla"))(Built)
  }

  val input = EventFrame("gc" -> EventFrame(
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

    (1 to 5000) foreach { i =>
      val input = EventFrame("gc" -> EventFrame(
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
    shouldNotBuild()
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

  val inputWithPoison = EventFrame("gc" -> EventFrame(
    "real" -> 0,
    "sys" -> 0.1,
    "user" -> 9.2
  ))

  "GroovyInstruction with dangerous config and poison input" should "raise events" in new WithDangerousConfig {
    expectEvent(inputWithPoison)(GroovyExecFailed, 'Error -> "Division by zero")
  }
  it should "not modify original json" in new WithDangerousConfig {
    expectOne(inputWithPoison) { result =>
      result #> 'gc +&> 'user should be(Some(9.2))
    }
  }

  trait WithCalculatorConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """j = json.content
          |streamId = j.streamId
          |v = j.value
          |counters = ctx.get(streamId)
          |if (counters == null) counters = 0
          |counters = counters + v
          |ctx.put(streamId, counters)
          |j.sum = counters
          |json""".stripMargin
    )
  }

  val inputStream1 = EventFrame(
    "value" -> 10,
    "streamId" -> "stream1"
  )
  val inputStream2 = EventFrame(
    "value" -> 23,
    "streamId" -> "stream2"
  )

  "GroovyInstruction with sum calculator using context" should "initialise map when first event arrives" in new WithCalculatorConfig {
    expectOne(inputStream1) { result =>
      result +> 'sum should be (Some(10))
    }
  }

  it should "increase the counter when next event arrives" in new WithCalculatorConfig {
    expectOne(inputStream1) { result => () }
    expectOne(inputStream1) { result =>
      result +> 'sum should be (Some(20))
    }
  }

  it should "initialise counter for the 1nd stream" in new WithCalculatorConfig {
    expectOne(inputStream1) { result => () }
    expectOne(inputStream1) { result =>
      result +> 'sum should be (Some(20))
    }
    expectOne(inputStream2) { result =>
      result +> 'sum should be (Some(23))
    }
  }
  it should "preserve counter of the 1st stream" in new WithCalculatorConfig {
    expectOne(inputStream1) { result => () }
    expectOne(inputStream1) { result =>
    }
    expectOne(inputStream2) { result =>
    }
    expectOne(inputStream1) { result =>
      result +> 'sum should be (Some(30))
    }
  }

  it should "preserve counter of the 2nd stream" in new WithCalculatorConfig {
    expectOne(inputStream1) { result => () }
    expectOne(inputStream1) { result =>
    }
    expectOne(inputStream2) { result =>
    }
    expectOne(inputStream1) { result =>
    }
    expectOne(inputStream2) { result =>
      result +> 'sum should be (Some(46))
    }
  }

  trait WithUsingNewJsonOpConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """j = newJson.apply()
          |j.content.streamId = "stream3"
          |j""".stripMargin
    )
  }

  "GroovyInstruction with newJson() op" should "create a new event" in new WithUsingNewJsonOpConfig {
    expectOne(inputStream1) { result =>
      result +> 'value should be (None)
      result ~> 'streamId should be (Some("stream3"))
    }
  }

  trait WithUsingCopyJsonOpConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """j = copyJson.apply()
          |j.content.streamId = "stream3"
          |j""".stripMargin
    )
  }

  "GroovyInstruction with copyJson() op" should "copy event" in new WithUsingCopyJsonOpConfig {
    expectOne(inputStream1) { result =>
      result +> 'value should be (Some(10))
      result ~> 'streamId should be (Some("stream3"))
    }
  }


  trait WithUsingCopyJsonOpMultiConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """j = copyJson.apply()
          |j.content.streamId = "stream3"
          |[j,json]""".stripMargin
    )
  }

  "GroovyInstruction returning array" should "return both copied and old events" in new WithUsingCopyJsonOpMultiConfig {
    expectN(inputStream1) { result =>
      result should have size 2
      result(0) +> 'value should be (Some(10))
      result(0) ~> 'streamId should be (Some("stream3"))
      result(1) +> 'value should be (Some(10))
      result(1) ~> 'streamId should be (Some("stream1"))
    }
  }

  trait WithDroppingConfig extends WithSimpleInstructionBuilder with GroovyInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GroovyInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "groovy",
      CfgFCode ->
        """j = copyJson.apply()
          |j.content.streamId = "stream3"
          |[]""".stripMargin
    )
  }

  "GroovyInstruction returning empty array" should "drop all events" in new WithDroppingConfig {
    expectNone(inputStream1)
  }


}

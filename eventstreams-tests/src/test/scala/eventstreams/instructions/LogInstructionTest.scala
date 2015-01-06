package eventstreams.instructions

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

import _root_.core.events.EventOps.symbolToEventOps
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class LogInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with LogInstructionConstants {
    override def builder: SimpleInstructionBuilder = new LogInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFEvent -> "eventname",
      CfgFLevel -> "INFO"
    )
  }

  s"LogInstruction with wrong config" should s"not build " in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new LogInstruction()

    override def config: JsValue = Json.obj(
      LogInstructionConstants.CfgFClass -> "log",
      LogInstructionConstants.CfgFEvent -> "eventname withspace",
      LogInstructionConstants.CfgFLevel -> "INFO"
    )

    shouldNotBuild()
  }

  s"LogInstruction with simple config" should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "raise event" in new WithBasicConfig {
    expectEvent(Json.obj("eventId" -> "id", "abc1" -> "bla", "tags" -> Json.arr("abc")))('eventname.info)
  }

  trait WithWarnConfig extends WithSimpleInstructionBuilder with LogInstructionConstants {
    override def builder: SimpleInstructionBuilder = new LogInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFEvent -> "eventname",
      CfgFLevel -> "WARN"
    )
  }

  it should "raise warn event" in new WithWarnConfig {
    expectEvent(Json.obj("eventId" -> "id", "abc1" -> "bla", "tags" -> Json.arr("abc")))('eventname.warn)
  }

  trait WithErrorConfig extends WithSimpleInstructionBuilder with LogInstructionConstants {
    override def builder: SimpleInstructionBuilder = new LogInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFEvent -> "eventname",
      CfgFLevel -> "ERROR"
    )
  }

  it should "raise error event" in new WithErrorConfig {
    expectEvent(Json.obj("eventId" -> "id", "abc1" -> "bla", "tags" -> Json.arr("abc")))('eventname.error)
  }


}

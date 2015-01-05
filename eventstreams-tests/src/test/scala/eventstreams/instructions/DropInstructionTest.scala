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

import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials.{DropInstruction, DropInstructionConstants}
import eventstreams.support.TestHelpers
import play.api.libs.json._

class DropInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with DropInstructionConstants {
    override def builder: SimpleInstructionBuilder = new DropInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "drop")
  }

  s"DropInstruction with simple config" should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "raise event when dropped" in new WithBasicConfig {
    expectEvent(Json.obj("eventId" -> "id", "abc1" -> "bla", "tags" -> Json.arr("abc")))(Dropped, 'EventId -> "id")
  }


}

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
import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials.{DropTagInstruction, DropTagInstructionConstants}
import eventstreams.support.TestHelpers
import play.api.libs.json._

class DropTagInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with DropTagInstructionConstants {
    override def builder: SimpleInstructionBuilder = new DropTagInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "droptag",
      CfgFTagToDrop -> "abc")
  }

  s"DropTagInstruction with simple config" should s"not build without required fields" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new DropTagInstruction()

    override def config: JsValue = Json.obj("class" -> "droptag")

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(Built)
  }

  it should "raise event when tag dropped" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla", "tags" -> Seq("abc")))(TagDropped, 'Tag -> "abc")
  }

  trait WithAdvancedConfig extends WithSimpleInstructionBuilder with DropTagInstructionConstants {
    override def builder: SimpleInstructionBuilder = new DropTagInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "droptag",
      CfgFTagToDrop -> "${source}_abc")
  }

  "DropTagInstruction with advanced config" should "be built with valid config" in new WithAdvancedConfig {
    shouldBuild()
  }

  it should "drop existing tag" in new WithAdvancedConfig {
    expectOne(EventFrame("abc1" -> 1, "source"->"tagname", "tags" -> Seq("tagname_abc", "tag2"))) { result =>
      result ##> 'tags should be(Some(List("tag2")))
    }
  }

  it should "not do anything if tag does not exists" in new WithAdvancedConfig {
    expectOne(EventFrame("abc1" -> 1, "source"->"tagname", "tags" -> Seq("tag1", "tagname_abcx"))) { result =>
      result ##> 'tags should be(Some(List("tag1", "tagname_abcx")))
    }
  }


}

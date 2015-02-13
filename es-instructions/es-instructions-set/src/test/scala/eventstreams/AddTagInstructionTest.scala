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

import eventstreams.instructions.{AddTagInstruction, AddTagInstructionConstants, SimpleInstructionBuilder}
import eventstreams.support.TestHelpers
import play.api.libs.json._

class AddTagInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with AddTagInstructionConstants {
    override def builder: SimpleInstructionBuilder = new AddTagInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "addtag",
      CfgFTagToAdd -> "abc")
  }

  s"AddTagInstruction with simple config" should s"not build without tagToAdd" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new AddTagInstruction()

    override def config: JsValue = Json.obj("class" -> "addtag")

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(Built, 'Tag -> "abc")
  }

  it should "raise event when tag added" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(TagAdded, 'Tag -> "abc")
  }

  trait WithAdvancedConfig extends WithSimpleInstructionBuilder with AddTagInstructionConstants {
    override def builder: SimpleInstructionBuilder = new AddTagInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "addtag",
      CfgFTagToAdd -> "${source}_abc")
  }

  "AddTagInstruction with advanced config" should "be built with valid config" in new WithAdvancedConfig {
    shouldBuild()
  }

  it should "add a new tag" in new WithAdvancedConfig {
    expectOne(EventFrame("abc1" -> 1, "source"->"tagname")) { result =>
      result ##> 'tags should be(Some(Seq("tagname_abc")))
    }
  }

  it should "add tag to existing tags" in new WithAdvancedConfig {
    expectOne(EventFrame("abc1" -> 1, "source"->"tagname", "tags" -> (Seq(("tag1"), ("tag2"))))) { result =>
      result ##> 'tags should be(Some(List(("tag1"), ("tag2"), ("tagname_abc"))))
    }
  }

  it should "not do anything if tag exists" in new WithAdvancedConfig {
    expectOne(EventFrame("abc1" -> 1, "source"->"tagname", "tags" -> (Seq(("tag1"), ("tagname_abc"))))) { result =>
      result ##> 'tags should be(Some(List(("tag1"), ("tagname_abc"))))
    }
  }


}

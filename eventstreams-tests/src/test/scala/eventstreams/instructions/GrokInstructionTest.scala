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

import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials.{GrokInstruction, GrokInstructionConstants}
import eventstreams.support.TestHelpers
import play.api.libs.json._

class GrokInstructionTest extends TestHelpers {


  trait WithMinimalConfig extends WithSimpleInstructionBuilder with GrokInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "grok",
      CfgFFields -> "${f}",
      CfgFGroups -> "f,v",
      CfgFPattern -> "([^=\\s]+)=([^=\\s]+)",
      CfgFSource -> "source",
      CfgFTypes -> "n",
      CfgFValues -> "${v}"
    )
  }

  s"DropTagInstruction with simple config" should s"not build without required fields (no ${GrokInstructionConstants.CfgFFields}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFGroups -> "f,v",
      GrokInstructionConstants.CfgFPattern -> "(\\w+?)=(\\d+?)",
      GrokInstructionConstants.CfgFSource -> "source",
      GrokInstructionConstants.CfgFTypes -> "n",
      GrokInstructionConstants.CfgFValues -> "${v}")

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${GrokInstructionConstants.CfgFGroups}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFFields -> "${f}",
      GrokInstructionConstants.CfgFPattern -> "(\\w+?)=(\\d+?)",
      GrokInstructionConstants.CfgFSource -> "source",
      GrokInstructionConstants.CfgFTypes -> "n",
      GrokInstructionConstants.CfgFValues -> "${v}")

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${GrokInstructionConstants.CfgFPattern}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFFields -> "${f}",
      GrokInstructionConstants.CfgFGroups -> "f,v",
      GrokInstructionConstants.CfgFSource -> "source",
      GrokInstructionConstants.CfgFTypes -> "n",
      GrokInstructionConstants.CfgFValues -> "${v}")

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${GrokInstructionConstants.CfgFSource}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFFields -> "${f}",
      GrokInstructionConstants.CfgFGroups -> "f,v",
      GrokInstructionConstants.CfgFPattern -> "(\\w+?)=(\\d+?)",
      GrokInstructionConstants.CfgFTypes -> "n",
      GrokInstructionConstants.CfgFValues -> "${v}")

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${GrokInstructionConstants.CfgFTypes}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFFields -> "${f}",
      GrokInstructionConstants.CfgFGroups -> "f,v",
      GrokInstructionConstants.CfgFPattern -> "(\\w+?)=(\\d+?)",
      GrokInstructionConstants.CfgFSource -> "source",
      GrokInstructionConstants.CfgFValues -> "${v}")

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${GrokInstructionConstants.CfgFValues}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      GrokInstructionConstants.CfgFClass -> "grok",
      GrokInstructionConstants.CfgFFields -> "${f}",
      GrokInstructionConstants.CfgFGroups -> "f,v",
      GrokInstructionConstants.CfgFPattern -> "(\\w+?)=(\\d+?)",
      GrokInstructionConstants.CfgFSource -> "source",
      GrokInstructionConstants.CfgFTypes -> "n"
    )

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithMinimalConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithMinimalConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  val originalSource = " field1=1  field2=20 field3=300"
  val input = Json.obj("source" -> originalSource)

  "Grok with input \"source\" -> \" field1=1  field2=20 field3=300\"" should "raise events when grokked" in new WithMinimalConfig {
    expectEvent(input)(Grokked, 'Field -> "/field1", 'Value -> "1", 'Type -> "n")
    expectEvent(input)(Grokked, 'Field -> "/field2", 'Value -> "20", 'Type -> "n")
    expectEvent(input)(Grokked, 'Field -> "/field3", 'Value -> "300", 'Type -> "n")
  }

  it should "set fields as per config" in new WithMinimalConfig {
    expectOne(input) { result =>
      result +> 'field1 should be (Some(1))
      result +> 'field2 should be (Some(20))
      result +> 'field3 should be (Some(300))
    }
  }

  it should "not touch original value" in new WithMinimalConfig {
    expectOne(input) { result =>
      result ~> 'source should be (Some(originalSource))
    }
  }


  trait WithMultifieldConfig extends WithSimpleInstructionBuilder with GrokInstructionConstants {
    override def builder: SimpleInstructionBuilder = new GrokInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "grok",
      CfgFFields -> "${f},tags,${f}_branch.name,values",
      CfgFGroups -> "f,v",
      CfgFPattern -> "([^=\\s]+)=([^=\\s]+)",
      CfgFSource -> "source",
      CfgFTypes -> "n,as,s,an",
      CfgFValues -> "${v},tag_${f},tag_${f},${v}"
    )
  }

  "Grok with input \"source\" -> \" field1=1  field2=20 field3=300\" and multifields config" should "raise events when grokked" in new WithMultifieldConfig {
    expectEvent(input)(Grokked, 'Field -> "/field1", 'Value -> "1", 'Type -> "n")
    expectEvent(input)(Grokked, 'Field -> "/tags", 'Value -> "tag_field1", 'Type -> "as")
    expectEvent(input)(Grokked, 'Field -> "/field1_branch/name", 'Value -> "tag_field1", 'Type -> "s")
    expectEvent(input)(Grokked, 'Field -> "/values", 'Value -> "1", 'Type -> "an")
    
    expectEvent(input)(Grokked, 'Field -> "/field2", 'Value -> "20", 'Type -> "n")
    expectEvent(input)(Grokked, 'Field -> "/tags", 'Value -> "tag_field2", 'Type -> "as")
    expectEvent(input)(Grokked, 'Field -> "/field2_branch/name", 'Value -> "tag_field2", 'Type -> "s")
    expectEvent(input)(Grokked, 'Field -> "/values", 'Value -> "20", 'Type -> "an")
  }

  it should "set numeric fields into /field1, etc" in new WithMultifieldConfig {
    expectOne(input) { result =>
      result +> 'field1 should be (Some(1))
      result +> 'field2 should be (Some(20))
      result +> 'field3 should be (Some(300))
    }
  }

  it should "set tags into /tags, etc" in new WithMultifieldConfig {
    expectOne(input) { result =>
      result ##> 'tags should be (Some(List(JsString("tag_field1"),JsString("tag_field2"),JsString("tag_field3"))))
    }
  }

  it should "set values into array /values, etc" in new WithMultifieldConfig {
    expectOne(input) { result =>
      result ##> 'values should be (Some(Seq(JsNumber(1),JsNumber(20),JsNumber(300))))
    }
  }


  it should "set field names into /field1_branch/name, etc" in new WithMultifieldConfig {
    expectOne(input) { result =>
      result #> 'field1_branch ~> 'name should be (Some("tag_field1"))
      result #> 'field2_branch ~> 'name should be (Some("tag_field2"))
      result #> 'field3_branch ~> 'name should be (Some("tag_field3"))
    }
  }


}

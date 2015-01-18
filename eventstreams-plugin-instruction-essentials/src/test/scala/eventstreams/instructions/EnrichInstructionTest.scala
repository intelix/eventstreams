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

import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials.{EnrichInstructionEvents, EnrichInstruction, EnrichInstructionConstants}
import eventstreams.support.TestHelpers
import play.api.libs.json.{JsNull, JsValue, Json}

class EnrichInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with EnrichInstructionConstants with EnrichInstructionEvents {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "enrich",
      CfgFFieldToEnrich -> "abc",
      CfgFTargetValueTemplate -> "${abc1}",
      CfgFTargetType -> "s")
  }

  "EnrichInstruction with simple config" should "not build without fieldName" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj("class" -> "enrich", "fieldValue" -> "${abc1}", "fieldType" -> "s")

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built, 'Field -> "abc", 'Type -> "s")
  }

  it should "raise event when enriched" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Enriched, 'Replacement -> "bla")
  }

  it should "enrich with macros" in new WithBasicConfig {
    expectOne(Json.obj("abc1" -> "bla")) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "use empty value when no source value is available" in new WithBasicConfig {
    expectOne(Json.obj("abcX" -> "bla")) { result =>
      result ~*> 'abc should be(Some(""))
    }
  }

  it should "override numeric value with string" in new WithBasicConfig {
    expectOne(Json.obj("abc1" -> "bla", "abc" -> 1)) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing value " in new WithBasicConfig {
    expectOne(Json.obj("abc1" -> "bla", "abc" -> "xyz")) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing object with string " in new WithBasicConfig {
    expectOne(Json.obj("abc1" -> "bla", "abc" -> Json.obj("hey"->"there"))) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing array with string " in new WithBasicConfig {
    expectOne(Json.obj("abc1" -> "bla", "abc" -> Json.arr("hey"))) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }


  trait WithComplexEnrich extends WithSimpleInstructionBuilder with EnrichInstructionConstants with EnrichInstructionEvents {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "enrich",
      CfgFFieldToEnrich -> "abc.xyz",
      CfgFTargetValueTemplate -> "${source/subsource1}_abc",
      CfgFTargetType -> "s")
  }

  "EnrichInstruction with complex config" should "be built with valid config" in new WithComplexEnrich {
    shouldBuild()
  }

  it should "raise event when built" in new WithComplexEnrich {
    expectEvent(Json.obj("source" -> Json.obj("subsource1" -> "bla")))(Built, 'Field -> "abc.xyz", 'Type -> "s")
  }

  it should "raise event when enriched" in new WithComplexEnrich {
    expectEvent(Json.obj("source" -> Json.obj("subsource1" -> "bla")))(Enriched, 'Replacement -> "bla_abc")
  }

  it should "enrich with macros" in new WithComplexEnrich {
    expectOne(Json.obj("source" -> Json.obj("subsource1" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("bla_abc"))
    }
  }

  it should "use empty value when no source value is available - no value" in new WithComplexEnrich {
    expectOne(Json.obj("source" -> Json.obj("subsource2" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }

  it should "use empty value when no source value is available - null branch" in new WithComplexEnrich {
    expectOne(Json.obj("source" -> JsNull)) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }

  it should "use empty value when no source value is available - missing branch" in new WithComplexEnrich {
    expectOne(Json.obj("sourcex" -> Json.obj("subsource1" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }


  trait WithNumericEnrich extends WithSimpleInstructionBuilder with EnrichInstructionConstants with EnrichInstructionEvents {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "enrich",
      CfgFFieldToEnrich -> "abc",
      CfgFTargetValueTemplate -> "${abc1}",
      CfgFTargetType -> "n")
  }

  "EnrichInstruction with numeric field" should "be built with valid config" in new WithNumericEnrich {
    shouldBuild()
  }

  it should "raise event when built" in new WithNumericEnrich {
    expectEvent(Json.obj("abc1" -> 1))(Built, 'Field -> "abc", 'Type -> "n")
  }

  it should "raise event when enriched" in new WithNumericEnrich {
    expectEvent(Json.obj("abc1" -> 1))(Enriched, 'Replacement -> "1")
  }

  it should "enrich with macros" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> 1)) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "enrich with macros and convert from strings" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> "1")) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing value" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> "1", "abc" -> 5)) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing string value" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> "1", "abc" -> "hey")) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing string value, consistently" in new WithNumericEnrich {
    (1 to 10000) foreach { i =>
      expectOne(Json.obj("abc1" -> i.toString, "abc" -> "hey")) { result =>
        result +> 'abc should be(Some(i))
      }
    }
  }

  it should "enrich with macros and convert from strings and support doubles" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> "1.1")) { result =>
      result +&> 'abc should be(Some(1.1))
    }
  }

  it should "enrich with macros and and support doubles" in new WithNumericEnrich {
    expectOne(Json.obj("abc1" -> 1.1)) { result =>
      result +&> 'abc should be(Some(1.1))
    }
  }

  it should "use empty value when no source value is available" in new WithNumericEnrich {
    expectOne(Json.obj("abcX" -> 1)) { result =>
      result +> 'abc should be(Some(0))
    }
  }

  it should "use empty value when not numeric" in new WithNumericEnrich {
    expectOne(Json.obj("abcX" -> "abc")) { result =>
      result +> 'abc should be(Some(0))
    }
  }
  
  trait WithStringEnrich extends WithSimpleInstructionBuilder with EnrichInstructionConstants {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "enrich",
      CfgFFieldToEnrich -> "abc",
      CfgFTargetValueTemplate -> "${abc1}",
      CfgFTargetType -> "s")
  }

  it should "override existing branch" in new WithStringEnrich {
    expectOne(Json.obj("abc1" -> "", "abc" -> Json.obj("x" -> "xyz"))) { result =>
      result ~*> 'abc should be(Some(""))
      result #> 'abc ~> 'x should be(None)
    }
  }





}

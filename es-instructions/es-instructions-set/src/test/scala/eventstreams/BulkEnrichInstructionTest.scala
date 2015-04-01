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

import eventstreams.EventFrameConverter.optionsConverter
import eventstreams.instructions._
import eventstreams.support.TestHelpers
import play.api.libs.json.{JsNull, JsValue, Json}

class BulkEnrichInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with BulkEnrichInstructionConstants with BulkEnrichInstructionSysevents {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "bulkenrich",
      CfgFEnrichmentRules -> "abc|s=${abc1}")
  }

  "BulkEnrichInstruction with simple config" should s"not build without enrichmentRules" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj("class" -> "bulkenrich")

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when enriched" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(Enriched, 'Replacement -> "bla")
  }

  it should "enrich with macros" in new WithBasicConfig {
    expectOne(EventFrame("abc1" -> "bla")) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "use empty value when no source value is available" in new WithBasicConfig {
    expectOne(EventFrame("abcX" -> "bla")) { result =>
      result ~*> 'abc should be(Some(""))
    }
  }

  it should "override numeric value with string" in new WithBasicConfig {
    expectOne(EventFrame("abc1" -> "bla", "abc" -> 1)) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing value " in new WithBasicConfig {
    expectOne(EventFrame("abc1" -> "bla", "abc" -> "xyz")) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing object with string " in new WithBasicConfig {
    expectOne(EventFrame("abc1" -> "bla", "abc" -> EventFrame("hey"->"there"))) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }

  it should "override existing array with string " in new WithBasicConfig {
    expectOne(EventFrame("abc1" -> "bla", "abc" -> Seq("hey"))) { result =>
      result ~> 'abc should be(Some("bla"))
    }
  }


  trait WithComplexEnrich extends WithSimpleInstructionBuilder with BulkEnrichInstructionConstants with BulkEnrichInstructionSysevents {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "bulkenrich",
      CfgFEnrichmentRules -> "abc.xyz|s=${source/subsource1}_abc")
  }

  "BulkEnrichInstruction with complex config" should "be built with valid config" in new WithComplexEnrich {
    shouldBuild()
  }

  it should "raise event when enriched" in new WithComplexEnrich {
    expectEvent(EventFrame("source" -> EventFrame("subsource1" -> "bla")))(Enriched, 'Replacement -> "bla_abc")
  }

  it should "enrich with macros" in new WithComplexEnrich {
    expectOne(EventFrame("source" -> EventFrame("subsource1" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("bla_abc"))
    }
  }

  it should "use empty value when no source value is available - no value" in new WithComplexEnrich {
    expectOne(EventFrame("source" -> EventFrame("subsource2" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }

  it should "use empty value when no source value is available - null branch" in new WithComplexEnrich {
    expectOne(EventFrame("source" -> JsNull)) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }

  it should "use empty value when no source value is available - missing branch" in new WithComplexEnrich {
    expectOne(EventFrame("sourcex" -> EventFrame("subsource1" -> "bla"))) { result =>
      result #> 'abc ~*> 'xyz should be(Some("_abc"))
    }
  }


  trait WithNumericEnrich extends WithSimpleInstructionBuilder with  BulkEnrichInstructionConstants with BulkEnrichInstructionSysevents {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "bulkenrich",
      CfgFEnrichmentRules -> "abc|n=${abc1}")
  }

  " BulkEnrichInstruction with numeric field" should "be built with valid config" in new WithNumericEnrich {
    shouldBuild()
  }

  it should "raise event when built" in new WithNumericEnrich {
    expectEvent(EventFrame("abc1" -> 1))(Built)
  }

  it should "raise event when enriched" in new WithNumericEnrich {
    expectEvent(EventFrame("abc1" -> 1))(Enriched, 'Replacement -> "1")
  }

  it should "enrich with macros" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> 1)) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "enrich with macros and convert from strings" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> "1")) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing value" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> "1", "abc" -> 5)) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing string value" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> "1", "abc" -> "hey")) { result =>
      result +> 'abc should be(Some(1))
    }
  }

  it should "override existing string value, consistently" in new WithNumericEnrich {
    (1 to 10000) foreach { i =>
      expectOne(EventFrame("abc1" -> i.toString, "abc" -> "hey")) { result =>
        result +> 'abc should be(Some(i))
      }
    }
  }

  it should "enrich with macros and convert from strings and support doubles" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> "1.1")) { result =>
      result +&> 'abc should be(Some(1.1))
    }
  }

  it should "enrich with macros and and support doubles" in new WithNumericEnrich {
    expectOne(EventFrame("abc1" -> 1.1)) { result =>
      result +&> 'abc should be(Some(1.1))
    }
  }

  it should "use empty value when no source value is available" in new WithNumericEnrich {
    expectOne(EventFrame("abcX" -> 1)) { result =>
      result +> 'abc should be(Some(0))
    }
  }

  it should "use empty value when not numeric" in new WithNumericEnrich {
    expectOne(EventFrame("abcX" -> "abc")) { result =>
      result +> 'abc should be(Some(0))
    }
  }
  
  trait WithStringEnrich extends WithSimpleInstructionBuilder with BulkEnrichInstructionConstants {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "bulkenrich",
      CfgFEnrichmentRules -> "abc|s=${abc1}")
  }

  it should "override existing branch" in new WithStringEnrich {
    expectOne(EventFrame("abc1" -> "", "abc" -> EventFrame("x" -> "xyz"))) { result =>
      result ~*> 'abc should be(Some(""))
      result #> 'abc ~> 'x should be(None)
    }
  }


  trait WithBulkEnrich extends WithSimpleInstructionBuilder with BulkEnrichInstructionConstants {
    override def builder: SimpleInstructionBuilder = new BulkEnrichInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "bulkenrich",
      CfgFEnrichmentRules ->
        """
          |abcS|s=${abc1}_str
          |abcN|n=1${abc1}""".stripMargin)
  }

  it should "process bulk enrichments" in new WithBulkEnrich {
    expectOne(EventFrame("abc1" -> "8")) { result =>
      result ~*> 'abcS should be(Some("8_str"))
      result +> 'abcN should be(Some(18))
    }
  }




}

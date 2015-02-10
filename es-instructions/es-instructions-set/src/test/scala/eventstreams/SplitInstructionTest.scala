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
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class SplitInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)",
      CfgFSource -> "source"
    )
  }
  
  import eventstreams.plugins.essentials.SplitInstructionConstants._

  s"SplitInstruction" should s"not build without $CfgFSource" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)"
    )

    shouldNotBuild()
  }

  it should s"not build without $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFSource -> "source"
    )

    shouldNotBuild()
  }

  it should s"not build with invalid $CfgFPattern" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "replace",
      CfgFSource -> "value",
      CfgFPattern -> "("
    )

    shouldNotBuild()
  }

  it should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(Built)
  }

  it should "split basic sequence into 2 events" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>first"))
      result(1) ~> 'source should be (Some("<s>second"))
    }
  }

  it should "properly use remainder in all cases - scenario 1" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "rest<s>bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethirdrest"))
    }
  }

  it should "properly use remainder in all cases - scenario 2" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "<s>bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethird"))
    }
  }

  it should "properly use remainder in all cases - scenario 3" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result =>
      result should have size 0
    }
  }

  it should "properly use remainder in all cases - scenario 4" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result => }
    expectN(EventFrame("source" -> ">bla")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
    }
  }
  it should "properly use remainder in all cases - scenario 5" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result => }
    expectN(EventFrame("source" -> ">bla<s>another")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
      result(1) ~> 'source should be (Some("<s>bla"))
    }
  }
  it should "properly use remainder in all cases - scenario 6" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result => }
    expectN(EventFrame("source" -> ">bla<s>")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>incompletethirdmore"))
      result(1) ~> 'source should be (Some("<s>bla"))
    }
  }
  it should "properly use remainder in all cases - scenario 7" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result => }
    expectN(EventFrame("source" -> ">bla<s>")) { result => }
    expectN(EventFrame("source" -> "another")) { result =>
      result should have size 0
    }
  }

  it should "properly use remainder in all cases - scenario 8" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result => }
    expectN(EventFrame("source" -> "more<s")) { result => }
    expectN(EventFrame("source" -> ">bla<s>")) { result => }
    expectN(EventFrame("source" -> "another")) { result => }
    expectN(EventFrame("source" -> "another<s>")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("<s>anotheranother"))
    }
  }
  it should "operate in utf8" in new WithBasicConfig {
    expectN(EventFrame("source" -> "bla<s>первый<s>второй<s>incompletethird")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("<s>первый"))
      result(1) ~> 'source should be (Some("<s>второй"))
    }
  }

  trait WithMultilineConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> "(?ms).*?^(.+?)(^.*)",
      CfgFSource -> "source"
    )
  }

  it should "support advanced patterns - multiline split" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird")) { result =>
      result should have size 3
      result(0) ~> 'source should be (Some("bla"))
      result(1) ~> 'source should be (Some("first"))
      result(2) ~> 'source should be (Some("second"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 2" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird\n")) { result =>
      result should have size 3
      result(0) ~> 'source should be (Some("bla"))
      result(1) ~> 'source should be (Some("first"))
      result(2) ~> 'source should be (Some("second"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 3" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(EventFrame("source" -> "\nsome")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("incompletethird"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 4" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(EventFrame("source" -> "\n")) { result => }
    expectN(EventFrame("source" -> "\n")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("incompletethird"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 5" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird")) { result => }
    expectN(EventFrame("source" -> "\n")) { result => }
    expectN(EventFrame("source" -> "another\nsome")) { result =>
      result should have size 2
      result(0) ~> 'source should be (Some("incompletethird"))
      result(1) ~> 'source should be (Some("another"))
    }
  }
  it should "support advanced patterns - multiline split - scenario 6" in new WithMultilineConfig {
    expectN(EventFrame("source" -> "bla\nfirst\nsecond\nincompletethird\n")) { result => }
    expectN(EventFrame("source" -> "\n")) { result => }
    expectN(EventFrame("source" -> "another")) { result =>
      result should have size 0
    }
    expectN(EventFrame("source" -> "another")) { result =>
      result should have size 0
    }
    expectN(EventFrame("source" -> "xx\nxx")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("anotheranotherxx"))
    }
  }

  trait WithNMONConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> "(?ms).*?^(ZZZZ,T\\d+.+?)(^ZZZZ,T.*)",
      CfgFSource -> "source"
    )
  }

  it should "support advanced patterns - NMON split" in new WithNMONConfig {
    expectN(EventFrame("source" -> "\n\nZZZZ,T00001\nCPU\nWHATEVER\nZZZZ,T00002")) { result =>
      result should have size 1
      result(0) ~> 'source should be (Some("ZZZZ,T00001\nCPU\nWHATEVER"))
    }

  }


  trait WithKeepOriginalConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)",
      CfgFSource -> "source",
      CfgFKeepOriginal -> true
    )
  }


  it should "keep original if requested" in new WithKeepOriginalConfig {
    expectN(EventFrame("source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 3
      result(2) ~> 'source should be (Some("bla<s>first<s>second<s>incompletethird"))
      result(0) ~> 'source should be (Some("<s>first"))
      result(1) ~> 'source should be (Some("<s>second"))
    }
  }

  it should "use unique ids" in new WithKeepOriginalConfig {
    expectN(EventFrame("eventId" -> "id", "source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 3

      result(2) ~> 'eventId should be (Some("id"))
      result(0) ~> 'eventId should be (Some("id:0"))
      result(1) ~> 'eventId should be (Some("id:1"))
    }
  }

  trait WithPersistenceSettingsConfig extends WithSimpleInstructionBuilder with SplitInstructionConstants {
    override def builder: SimpleInstructionBuilder = new SplitInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "split",
      CfgFPattern -> ".*?(<s>.+?)(<s>.*)",
      CfgFSource -> "source",
      CfgFIndex -> "${index}",
      CfgFTable -> "${table}",
      CfgFTTL -> "${_ttl}",
      CfgFAdditionalTags -> "tag1,tag2"
    )
  }

  it should "copy persistence settings if requested" in new WithPersistenceSettingsConfig {
    expectN(EventFrame("eventId" -> "id", "index" -> "i", "_ttl"->"1d", "table" -> "t", "source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 2

      result(0) ~> 'index should be (Some("i"))
      result(1) ~> 'index should be (Some("i"))
      result(0) ~> 'table should be (Some("t"))
      result(1) ~> 'table should be (Some("t"))
      result(0) ~> '_ttl should be (Some("1d"))
      result(1) ~> '_ttl should be (Some("1d"))
    }
  }

  it should "populate tags as requested" in new WithPersistenceSettingsConfig {
    expectN(EventFrame("eventId" -> "id", "index" -> "i", "_ttl"->"1d", "table" -> "t", "source" -> "bla<s>first<s>second<s>incompletethird")) { result =>
      result should have size 2

      result(0) ##> 'tags should be (Some(List(("tag1"), ("tag2"))))
      result(1) ##> 'tags should be (Some(List(("tag1"), ("tag2"))))
    }
  }




}

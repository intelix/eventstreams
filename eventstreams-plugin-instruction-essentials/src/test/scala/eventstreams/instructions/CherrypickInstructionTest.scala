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
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class CherrypickInstructionTest extends TestHelpers with CherrypickInstructionConstants {


  trait WithBasicConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new CherrypickInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "cherrypick",
      CfgFFieldName -> "value",
      CfgFFieldValuePath -> "source/branch"
    )
  }

  s"CherrypickInstruction with simple config" should s"not build without $CfgFFieldName" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new CherrypickInstruction()

    override def config: JsValue = Json.obj(CfgFClass -> "cherrypick", CfgFFieldValuePath -> "source/branch")

    shouldNotBuild()
  }

  it should s"not build without $CfgFFieldValuePath" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new EnrichInstruction()

    override def config: JsValue = Json.obj(CfgFClass -> "cherrypick", CfgFFieldName -> "branch")

    shouldNotBuild()
  }

  it should "be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "raise event when tag added" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123))))(Cherrypicked)
  }

  it should "produce two events" in new WithBasicConfig {
    expectN(Json.obj("abc1" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      result should have size 2
      (result(1) #> 'value ~> 'f1) should be(Some("abc"))
      (result(1) #> 'value +> 'f2) should be(Some(123))
      (result(0) ~> 'abc1) should be(Some("bla"))
    }
  }

  trait WithAdvacedConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new CherrypickInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "cherrypick",
      CfgFFieldName -> "value",
      CfgFFieldValuePath -> "source/branch",
      CfgFAdditionalTags -> "tag1,tag2",
      CfgFEventIdTemplate -> "${eventId}_picked",
      CfgFIndex -> "${index}",
      CfgFTable -> "${table}",
      CfgFTTL -> "${_ttl}",
      CfgFKeepOriginal -> false
    )
  }

  "CherrypickInstruction with advanced config" should "produce one event" in new WithAdvacedConfig {
    expectOne(Json.obj("abc1" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
    }
  }

  it should "drop the original" in new WithAdvacedConfig {
    expectOne(Json.obj("abc1" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result #> 'value ~> 'f1) should be(Some("abc"))
      (result #> 'value +> 'f2) should be(Some(123))
    }
  }

  it should "add new tags" in new WithAdvacedConfig {
    expectOne(Json.obj("abc1" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result ##> 'tags ) should be(Some(List(JsString("tag1"),JsString("tag2"))))
    }
  }

  it should "not use tags from the original event" in new WithAdvacedConfig {
    expectOne(Json.obj("abc1" -> "bla", "tags" -> JsArray(Seq(JsString("tx"))), "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result ##> 'tags ) should be(Some(List(JsString("tag1"),JsString("tag2"))))
    }
  }

  it should "populate index" in new WithAdvacedConfig {
    expectOne(Json.obj("index" -> "bla", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result ~> 'index ) should be(Some("bla"))
    }
  }

  it should "populate eventId" in new WithAdvacedConfig {
    expectOne(Json.obj("eventId" -> "id", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result ~> 'eventId ) should be(Some("id_picked"))
    }
  }

  it should "populate ttl" in new WithAdvacedConfig {
    expectOne(Json.obj("_ttl" -> "1d", "source" -> Json.obj("branch" -> Json.obj("f1"->"abc", "f2"->123)))) { result =>
      (result ~> '_ttl ) should be(Some("1d"))
    }
  }




}

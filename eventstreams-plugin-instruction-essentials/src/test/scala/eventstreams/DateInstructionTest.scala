package eventstreams

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
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class DateInstructionTest extends TestHelpers with DateInstructionConstants {


  trait WithMinimalFromTsConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "date",
      CfgFSource -> "sourceField"
    )
  }

  trait WithMinimalFromStringConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "date",
      CfgFSource -> "sourceField",
      CfgFSourceZone -> "Australia/Sydney",
      CfgFPattern -> "yyyy-MMM-dd"
    )
  }

  s"DateInstruction" should s"not build without $CfgFSource" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(CfgFClass -> "date")

    shouldNotBuild()
  }
  it should s"not build with invalid source zone" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(CfgFClass -> "date", CfgFSource -> "sourceField", CfgFSourceZone -> "Sydney")

    shouldNotBuild()
  }
  it should s"not build with invalid target zone" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(CfgFClass -> "date", CfgFSource -> "sourceField", CfgFTargetZone -> "Sydney")

    shouldNotBuild()
  }
  it should "be built with valid config" in new WithMinimalFromTsConfig {
    shouldBuild()
  }

  s"DateInstruction WithMinimalFromTsConfig" should "raise event when built" in new WithMinimalFromTsConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "properly parse a valid input" in new WithMinimalFromTsConfig {
    expectEvent(Json.obj("sourceField" -> System.currentTimeMillis()))(DateParsed)
  }
  it should "not parse an invalid input" in new WithMinimalFromTsConfig {
    expectEvent(Json.obj("abc" -> System.currentTimeMillis()))(UnableToParseDate)
  }
  it should "not parse an invalid input (string)" in new WithMinimalFromTsConfig {
    expectEvent(Json.obj("sourceField" -> "123"))(UnableToParseDate)
  }


  s"DateInstruction WithMinimalFromStringConfig (syd source)" should "raise event when built" in new WithMinimalFromStringConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "properly parse a valid input" in new WithMinimalFromStringConfig {
    expectEvent(Json.obj("sourceField" -> "2014-Jan-10"))(DateParsed)
  }
  it should "not parse an invalid input" in new WithMinimalFromStringConfig {
    expectEvent(Json.obj("abc" -> System.currentTimeMillis()))(UnableToParseDate)
  }
  it should "not parse an invalid input (long)" in new WithMinimalFromStringConfig {
    expectEvent(Json.obj("sourceField" -> System.currentTimeMillis()))(UnableToParseDate)
  }

  it should "populate default ts field" in new WithMinimalFromStringConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10")) { result =>
      (result ++> default_targetTsField ) should be(Some(1389272400000L))
    }
  }
  it should "populate default fmt field" in new WithMinimalFromStringConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10")) { result =>
      (result ~> default_targetFmtField ) should be(Some("2014-01-10T00:00:00.000+11:00"))
    }
  }

  trait WithMinimalFromStringWithTimeConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "date",
      CfgFSource -> "sourceField",
      CfgFSourceZone -> "Australia/Sydney",
      CfgFPattern -> "yyyy-MMM-dd HH:mm:ss.SSS"
    )
  }

  s"DateInstruction WithMinimalFromStringConfig (syd source, with time)" should "populate all components" in new WithMinimalFromStringWithTimeConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10 22:11:12.123")) { result =>
      (result ~> default_targetFmtField ) should be(Some("2014-01-10T22:11:12.123+11:00"))
    }
  }
  it should "not process invalid sources (pattern mismatch)" in new WithMinimalFromStringWithTimeConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10 22:11:12")) { result =>
      (result ~> default_targetFmtField ) should be(None)
    }
  }
  it should "not process invalid sources (invalid minute value)" in new WithMinimalFromStringWithTimeConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10 22:61:12")) { result =>
      (result ~> default_targetFmtField ) should be(None)
    }
  }

  trait WithMinimalFromStringWithTargetZoneConfig extends WithSimpleInstructionBuilder  {
    override def builder: SimpleInstructionBuilder = new DateInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "date",
      CfgFSource -> "sourceField",
      CfgFSourceZone -> "Australia/Sydney",
      CfgFTargetFmtField -> "target/field",
      CfgFTargetTsField -> "target/tsfield",
      CfgFTargetZone -> "Asia/Tashkent",
      CfgFPattern -> "yyyy-MMM-dd HH:mm:ss.SSS"
    )
  }

  s"DateInstruction WithMinimalFromStringWithTargetZoneConfig" should "populate all components into custom field" in new WithMinimalFromStringWithTargetZoneConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10 22:11:12.123")) { result =>
      (result #> 'target ~> "field" ) should be(Some("2014-01-10T16:11:12.123+05:00"))
    }
  }
  it should "populate ts into custom field" in new WithMinimalFromStringWithTargetZoneConfig {
    expectOne(Json.obj("sourceField" -> "2014-Jan-10 22:11:12.123")) { result =>
      (result #> 'target ++> "tsfield" ) should be(Some(1389352272123L))
    }
  }

}

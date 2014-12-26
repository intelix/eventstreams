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

import _root_.core.events.EventOps.symbolToEventOps
import eventstreams.core.Tools.configHelper
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.plugins.essentials._
import eventstreams.support.TestHelpers
import play.api.libs.json._

class PersistenceInstructionTest extends TestHelpers {


  trait WithBasicConfig extends WithSimpleInstructionBuilder with PersistenceInstructionConstants {
    override def builder: SimpleInstructionBuilder = new PersistenceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFIndex -> "${cfg.index}",
      CfgFTable -> "${cfg.table}",
      CfgFTTL -> "${cfg.ttl}"
    )
  }

  import PersistenceInstructionConstants._

  s"PersistenceInstruction" should s"not build if $CfgFIndex is missing" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new PersistenceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFTable -> "${cfg.table}",
      CfgFTTL -> "${cfg.ttl}"
    )

    shouldNotBuild()
  }

  it should s"not build if $CfgFTTL is missing" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new PersistenceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFIndex -> "${cfg.index}",
      CfgFTable -> "${cfg.table}"
    )

    shouldNotBuild()
  }

  it should s"not build if $CfgFTable is missing" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new PersistenceInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "log",
      CfgFIndex -> "${cfg.index}",
      CfgFTTL -> "${cfg.ttl}"
    )

    shouldNotBuild()
  }

  it should s"be built with valid config" in new WithBasicConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithBasicConfig {
    expectEvent(Json.obj("abc1" -> "bla"))(Built)
  }

  it should "set values as configured" in new WithBasicConfig {
    expectOne(Json.obj("cfg" -> Json.obj("index" -> "i", "ttl"->"1d", "table" -> "t"))) { result =>
      result ~> '_ttl should be (Some("1d"))
      result ~> 'index should be (Some("i"))
      result ~> 'table should be (Some("t"))
    }
  }


}

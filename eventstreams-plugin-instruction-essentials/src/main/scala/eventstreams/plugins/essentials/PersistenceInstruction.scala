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

package eventstreams.plugins.essentials

import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.SimpleInstructionBuilder
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._

class PersistenceInstruction extends SimpleInstructionBuilder {
  val configId = "persistence"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      index <- props ~> 'index \/> Fail(s"Invalid persistence instruction. Missing 'index' value. Contents: ${Json.stringify(props)}");
      table <- props ~> 'table \/> Fail(s"Invalid persistence instruction. Missing 'table' value. Contents: ${Json.stringify(props)}");
      ttl <- props ~> 'ttl \/> Fail(s"Invalid persistence instruction. Missing 'ttl' value. Contents: ${Json.stringify(props)}")
    ) yield {

      frame: JsonFrame => {

        List(JsonFrame(
          frame.event.set(
            __ \ 'index -> JsString(macroReplacement(frame,index)),
            __ \ 'table -> JsString(macroReplacement(frame,table)),
            __ \ '_ttl -> JsString(macroReplacement(frame,ttl))),
          frame.ctx))

      }
    }


}

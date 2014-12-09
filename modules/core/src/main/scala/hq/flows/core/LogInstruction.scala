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

package hq.flows.core

import com.typesafe.scalalogging.Logger
import common.{JsonFrame, Fail}
import common.ToolExt.configHelper
import hq.flows.core.Builder.{SimpleInstructionType, InstructionType}
import org.slf4j.LoggerFactory
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

private[core] object LogInstruction extends SimpleInstructionBuilder {
  val configId = "log"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] = {

    val level = props ~> 'level | "INFO"
    val loggerName = props ~> 'logger | "default"

    val baseLogger = Logger(LoggerFactory getLogger loggerName)
    val loggerForLevel = level.toUpperCase match {
      case "DEBUG" => baseLogger.debug(_: String)
      case "INFO" => baseLogger.info(_: String)
      case "WARN" => baseLogger.warn(_: String)
      case "ERROR" => baseLogger.error(_: String)
    }

    \/- { frame: JsonFrame =>
      loggerForLevel(frame.toString)
      List(frame)
    }

  }
}

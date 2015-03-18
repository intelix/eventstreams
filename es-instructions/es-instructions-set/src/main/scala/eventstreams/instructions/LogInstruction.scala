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

package eventstreams.instructions

import com.typesafe.scalalogging.Logger
import core.sysevents.SyseventOps.{stringToSyseventOps, symbolToSyseventOps}
import core.sysevents.ref.ComponentWithBaseSysevents
import core.sysevents.{FieldAndValue, WithSyseventPublisher}
import eventstreams.Tools.configHelper
import eventstreams.instructions.Types.SimpleInstructionType
import eventstreams.{EventFrame, Fail, UUIDTools}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

trait LogInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace

  override def componentId: String = "Instruction.Log"
}

trait LogInstructionConstants extends InstructionConstants {
  val CfgFLevel = "level"
  val CfgFEvent = "event"

}

object LogInstructionConstants extends LogInstructionConstants with LogInstructionSysevents

class LogInstruction extends SimpleInstructionBuilder with LogInstructionConstants with LogInstructionSysevents with WithSyseventPublisher {
  val configId = "log"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] = {

    val level = props ~> CfgFLevel | "INFO"
    props ~> CfgFEvent match {
      case None => Fail(s"Invalid $configId instruction. Missing '$CfgFEvent' value. Contents: ${Json.stringify(props)}")
      case Some(loggerName) if "^\\w[\\w\\d]*$".r.findFirstMatchIn(loggerName).isEmpty => Fail(s"Invalid $configId instruction. $CfgFEvent must start with a character and contain only characters and numbers. Contents: ${Json.stringify(props)}")
      case Some(loggerName) =>
        val baseLogger = Logger(LoggerFactory getLogger loggerName)
        val loggerForLevel = level.toUpperCase match {
          case "DEBUG" => loggerName.trace >> (_: FieldAndValue)
          case "INFO" => loggerName.info >> (_: FieldAndValue)
          case "WARN" => loggerName.warn >> (_: FieldAndValue)
          case "ERROR" => loggerName.error >> (_: FieldAndValue)
        }


        \/- {

          val uuid = UUIDTools.generateShortUUID

          Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

          frame: EventFrame =>
            loggerForLevel('Frame -> frame.toString)
            List(frame)
        }
    }


  }
}

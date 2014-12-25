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

import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEvents
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.{InstructionConstants, SimpleInstructionBuilder}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.mutable
import scalaz.Scalaz._
import scalaz._

trait IntervalCalcInstructionEvents
  extends ComponentWithBaseEvents
  with WithEvents {

  val Built = 'Built.trace
  val IntervalCalcInitialised = 'IntervalCalcInitialised.trace
  val IntervalCalcReset = 'IntervalCalcReset.trace
  val IntervalCalculated = 'IntervalCalculated.trace
  val IntervalCalcSkipped = 'IntervalCalcSkipped.trace

  override def id: String = "Instruction.IntervalCalc"
}

trait IntervalCalcInstructionConstants extends InstructionConstants with IntervalCalcInstructionEvents {
  val CfgFIntervalFieldName = "intervalFieldName"
  val CfgFTimestampField = "timestampField"
  val CfgFStreamId = "streamIdTemplate"

}

object IntervalCalcInstructionConstants extends IntervalCalcInstructionConstants

class IntervalCalcInstruction extends SimpleInstructionBuilder with IntervalCalcInstructionConstants {
  val configId = "intervalcalc"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFIntervalFieldName \/> Fail(s"Invalid $configId instruction. Missing '$CfgFIntervalFieldName' value. Contents: ${Json.stringify(props)}");
      streamIdTemplate <- props ~> CfgFStreamId \/> Fail(s"Invalid $configId instruction. Missing '$CfgFStreamId' value. Contents: ${Json.stringify(props)}");
      tsField <- props ~> CfgFTimestampField \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTimestampField' value. Contents: ${Json.stringify(props)}")
    ) yield {

      // TODO clean old entries
      var streams = mutable.Map[String, Option[Long]]()

      val uuid = Utils.generateShortUUID

      Built >>('Config --> Json.stringify(props), 'InstructionInstanceId --> uuid)

      frame: JsonFrame => {

        val eventId = frame.event ~> 'eventId | "n/a"

        val streamId = macroReplacement(frame, streamIdTemplate).trim
        
        if (streamId.isEmpty) {
          IntervalCalcSkipped >>('Reason --> s"No stream id in $streamIdTemplate", 'EventId --> eventId, 'InstructionInstanceId --> uuid)
          List(frame)
        } else {
            val v = frame.event ++> tsField | 0

            def initialise() = {
              IntervalCalcInitialised >>('StreamId --> streamId, 'Initial --> v, 'EventId --> eventId, 'InstructionInstanceId --> uuid)
              streams += streamId -> Some(v)
              List(frame)
            }
            def reset(last: Long) = {
              IntervalCalcReset >>('StreamId --> streamId, 'Last --> last, 'Current --> v, 'EventId --> eventId, 'InstructionInstanceId --> uuid)
                streams += streamId -> Some(v)
              List(frame)
            }
            def calculate(last: Long) = {
              // TODO test what happens if path is invalid, or fieldname is invalid
              val keyPath = toPath(macroReplacement(frame, JsString(fieldName)).as[String])
              val interval = v - last
              streams += streamId -> Some(v)
              IntervalCalculated >>('StreamId --> streamId, 'Interval --> interval, 'EventId --> eventId, 'InstructionInstanceId --> uuid)
              List(frame.copy(event = frame.event.set(keyPath -> JsNumber(interval))))
            }


            streams.getOrElse(streamId, None) match {
              case None if v > 0 => initialise()
              case Some(last) if v < last => reset(last)
              case Some(last) if v >= last => calculate(last)
              case _ =>
                IntervalCalcSkipped >>('Reason --> s"Invalid ts? Value: $v",  'EventId --> eventId, 'InstructionInstanceId --> uuid)
                List(frame)
            }

        }

      }
    }


}

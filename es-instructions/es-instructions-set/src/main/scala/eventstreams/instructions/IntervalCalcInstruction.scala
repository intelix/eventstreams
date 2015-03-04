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

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.{configHelper, _}
import eventstreams._
import eventstreams.instructions.Types.SimpleInstructionType
import play.api.libs.json._

import scala.collection.mutable
import scalaz.Scalaz._
import scalaz._

trait IntervalCalcInstructionSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val IntervalCalcInitialised = 'IntervalCalcInitialised.trace
  val IntervalCalcReset = 'IntervalCalcReset.trace
  val IntervalCalculated = 'IntervalCalculated.trace
  val IntervalCalcSkipped = 'IntervalCalcSkipped.trace

  override def componentId: String = "Instruction.IntervalCalc"
}

trait IntervalCalcInstructionConstants extends InstructionConstants with IntervalCalcInstructionSysevents {
  val CfgFIntervalFieldName = "intervalFieldName"
  val CfgFTimestampField = "timestampField"
  val CfgFStreamKey = "streamKeyTemplate"

}

object IntervalCalcInstructionConstants extends IntervalCalcInstructionConstants

class IntervalCalcInstruction extends SimpleInstructionBuilder with IntervalCalcInstructionConstants with WithSyseventPublisher {
  val configId = "intervalcalc"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      fieldName <- props ~> CfgFIntervalFieldName \/> Fail(s"Invalid $configId instruction. Missing '$CfgFIntervalFieldName' value. Contents: ${Json.stringify(props)}");
      streamKeyTemplate <- props ~> CfgFStreamKey \/> Fail(s"Invalid $configId instruction. Missing '$CfgFStreamKey' value. Contents: ${Json.stringify(props)}");
      tsField <- props ~> CfgFTimestampField \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTimestampField' value. Contents: ${Json.stringify(props)}")
    ) yield {

      // TODO clean old entries
      var streams = mutable.Map[String, Option[Long]]()

      val uuid = UUIDTools.generateShortUUID

      Built >>('Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)

      frame: EventFrame => {

        val eventId = frame.eventIdOrNA

        val streamKey = macroReplacement(frame, streamKeyTemplate).trim

        if (streamKey.isEmpty) {
          IntervalCalcSkipped >>('Reason -> s"No stream id in $streamKeyTemplate", 'EventId -> eventId, 'InstructionInstanceId -> uuid)
          List(frame)
        } else {
          val v = frame ++> tsField | 0

          def initialise() = {
            IntervalCalcInitialised >>('StreamKey -> streamKey, 'Initial -> v, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
            streams += streamKey -> Some(v)
            List(frame)
          }
          def reset(last: Long) = {
            IntervalCalcReset >>('StreamKey -> streamKey, 'Last -> last, 'Current -> v, 'EventId -> eventId, 'InstructionInstanceId -> uuid)
            streams += streamKey -> Some(v)
            List(frame)
          }
          def calculate(last: Long) = {
            // TODO test what happens if path is invalid, or fieldname is invalid
            val keyPath = EventValuePath(macroReplacement(frame, fieldName))
            val interval = v - last
            streams += streamKey -> Some(v)
            IntervalCalculated >>('StreamKey -> streamKey, 'Interval -> interval, 'EventId -> eventId, 'InstructionInstanceId -> uuid)

            List(keyPath.setLongInto(frame, interval))
          }


          streams.getOrElse(streamKey, None) match {
            case None if v > 0 => initialise()
            case Some(last) if v < last => reset(last)
            case Some(last) if v >= last => calculate(last)
            case _ =>
              IntervalCalcSkipped >>('Reason -> s"Invalid ts? Value: $v", 'EventId -> eventId, 'InstructionInstanceId -> uuid)
              List(frame)
          }

        }

      }
    }


}

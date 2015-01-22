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

class IntervalCalcInstructionTest extends TestHelpers {


  trait WithMinimalConfig extends WithSimpleInstructionBuilder with IntervalCalcInstructionConstants {
    override def builder: SimpleInstructionBuilder = new IntervalCalcInstruction()

    override def config: JsValue = Json.obj(
      CfgFClass -> "intervalcalc",
      CfgFIntervalFieldName -> "interval",
      CfgFStreamId -> "${streamId}",
      CfgFTimestampField -> "ts"
    )
  }

  s"IntervalCalcInstruction with simple config" should s"not build without required fields (no ${IntervalCalcInstructionConstants.CfgFIntervalFieldName}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new IntervalCalcInstruction()

    override def config: JsValue = Json.obj(
      IntervalCalcInstructionConstants.CfgFClass -> "intervalcalc",
      IntervalCalcInstructionConstants.CfgFStreamId -> "streamId",
      IntervalCalcInstructionConstants.CfgFTimestampField -> "ts"
    )

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${IntervalCalcInstructionConstants.CfgFStreamId}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new IntervalCalcInstruction()

    override def config: JsValue = Json.obj(
      IntervalCalcInstructionConstants.CfgFClass -> "intervalcalc",
      IntervalCalcInstructionConstants.CfgFIntervalFieldName -> "interval",
      IntervalCalcInstructionConstants.CfgFTimestampField -> "ts"
    )

    shouldNotBuild()
  }

  it should s"not build without required fields (no ${IntervalCalcInstructionConstants.CfgFTimestampField}})" in new WithSimpleInstructionBuilder {
    override def builder: SimpleInstructionBuilder = new IntervalCalcInstruction()

    override def config: JsValue = Json.obj(
      IntervalCalcInstructionConstants.CfgFClass -> "intervalcalc",
      IntervalCalcInstructionConstants.CfgFIntervalFieldName -> "interval",
      IntervalCalcInstructionConstants.CfgFStreamId -> "${streamId}"
    )

    shouldNotBuild()
  }

  it should "build with valid config" in new WithMinimalConfig {
    shouldBuild()
  }

  it should "raise event when built" in new WithMinimalConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(Built)
  }

  "IntervalCalcInstruction" should "skip events without designated streamId field" in new WithMinimalConfig {
    expectEvent(EventFrame("abc1" -> "bla"))(IntervalCalcSkipped)
  }

  it should "skip initialisation if ts is empty" in new WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1"))(IntervalCalcSkipped)
  }

  val ts = System.currentTimeMillis()

  it should "initialise on first event with streamId and proper ts" in new WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcInitialised)
  }

  it should "not set interval field when initialised" in new WithMinimalConfig {
    expectOne(EventFrame("streamId" -> "stream1", "ts" -> ts)) { result =>
      result ++> 'interval should be(None)
    }
  }

  it should "calculate interval on second event" in new WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcInitialised)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 100)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 100)
  }

  it should "calculate interval and update fields on second event" in new WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcInitialised)
    expectOne(EventFrame("streamId" -> "stream1", "ts" -> (ts + 100))) { result =>
      result ++> 'interval should be(Some(100))
    }
  }

  it should "calculate interval on third event" in new WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcInitialised)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 100)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 100)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 110)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 10)
  }

  trait WithCalculated extends WithMinimalConfig {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcInitialised)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 100)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 100)
  }

  "IntervalCalcInstruction with one calculated interval" should "reset if next event ts is out of order" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 90)))(IntervalCalcReset)
  }
  it should "calc another interval if ts value is higher" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 190)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 90)
  }
  it should "calc another interval if ts value is the same" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 100)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 0)
  }
  it should "start another interval if streamID is different" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 100)))(IntervalCalcInitialised, 'StreamId -> "stream2")
  }
  it should "reset if ts is missing" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream1"))(IntervalCalcReset, 'StreamId -> "stream1")
  }
  it should "reset if ts is lower" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcReset, 'StreamId -> "stream1")
  }
  it should "handle two intervals independently" in new WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 100)))(IntervalCalcInitialised, 'StreamId -> "stream2")
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 200)))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> 100)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 120)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 20)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 130)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 10)
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 201)))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> 1)
  }

  trait WithTwoIntervalsOneReset extends WithCalculated {
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 100)))(IntervalCalcInitialised, 'StreamId -> "stream2")
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 200)))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> 100)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 120)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 20)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 130)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 10)
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 201)))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> 1)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> ts))(IntervalCalcReset, 'StreamId -> "stream1")
  }

  "IntervalCalcInstruction with one reset one calculated intervals" should "continue after reset" in new WithTwoIntervalsOneReset {
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 90)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 90)
  }

  it should "ignore invalid events and then continue normal operation" in new WithTwoIntervalsOneReset {
    expectEvent(EventFrame("ts" -> (ts + 90)))(IntervalCalcSkipped)
    expectEvent(EventFrame("streamId" -> "stream1", "ts" -> (ts + 90)))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> 90)
    expectEvent(EventFrame("streamId" -> "stream2", "ts" -> (ts + 290)))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> 89)
  }

  it should "consistently produce correct values" in new WithTwoIntervalsOneReset {
    var shift1 = ts + 0
    var shift2 = ts + 201
    (1 to 10000) foreach { i =>
      shift1 += i
      shift2 += i
      expectEvent(EventFrame("streamId" -> "stream1", "ts" -> shift1))(IntervalCalculated, 'StreamId -> "stream1", 'Interval -> i)
      expectEvent(EventFrame("streamId" -> "stream2", "ts" -> shift2))(IntervalCalculated, 'StreamId -> "stream2", 'Interval -> i)
      
    }
  }


}

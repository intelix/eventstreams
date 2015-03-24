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

package eventstreams.gauges

import eventstreams.core.actors.ActorWithComposableBehavior
import eventstreams.signals.{SignalEventFrame, SignalKey}
import eventstreams.{NowProvider, WithCHMetrics}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._

trait MetricAccounting extends NowProvider with WithCHMetrics with ActorWithComposableBehavior {

  val LevelUnknown = 0
  val LevelGreen = 1
  val LevelYellow = 2
  val LevelRed = 3

  protected var tsOfLastSignal: Option[Long] = None

  protected var sigUnit: Option[String] = None
  protected var sigSamplingRateMs: Option[Int] = None
  protected var sigTTLMs: Option[Long] = None

  def signalKey: SignalKey
  def toValuesData: Option[JsValue]
  def toData: Option[JsValue] = toValuesData.map { v =>
    Json.obj(
      "v" -> v,
      "l" -> currentLevel
    )
  }
  def currentLevel: Int

  def updateUnit(s: String) = sigUnit = Some(s)
  def updateSamplingRateMs(v: Int) = sigSamplingRateMs = Some(v)
  def updateTTLMs(v: Long) = sigTTLMs = Some(v)

  def isSignalValueExpired: Boolean = isSignalValueExpiredOption | false
  private def isSignalValueExpiredOption: Option[Boolean] =
    for (
      ttl <- sigTTLMs if ttl > 0;
      ts <- tsOfLastSignal
    ) yield now > ts + ttl


  def checkAndMarkLatestSignal(frame: SignalEventFrame) =
    frame.sigTs match {
      case b@Some(candidateTs) =>
        tsOfLastSignal match {
          case Some(currentTs) if currentTs > candidateTs =>
            false
          case _ =>
            tsOfLastSignal = b
            true
        }
      case _ =>
        tsOfLastSignal = None
        true
    }

  def update(sig: SignalEventFrame): Unit = {
    sig.sigUnit foreach updateUnit
    sig.sigSamplingRateMs foreach updateSamplingRateMs
    sig.sigTTLMs foreach updateTTLMs
  }


}

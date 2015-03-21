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

import java.util.concurrent.TimeUnit

import eventstreams.signals.SignalEventFrame
import nl.grons.metrics.scala.Timer
import play.api.libs.json.{JsString, JsValue}

import scalaz.Scalaz._

trait TimingMetricAccounting extends NumericMetricAccounting with WithMetric[Timer] {

  private var timeUnit: TimeUnit = TimeUnit.MILLISECONDS

  override def createMetric(metricName: String): Timer = metrics.timer(metricName)

  override def updateValue(v: Double): Unit = {
    val ms = calculateMsFrom(v)
    m.foreach(_.update((ms * 1000).toLong, TimeUnit.MICROSECONDS))
    super.updateValue(ms)
  }

  private def calculateMsFrom(v: Double) =
    timeUnit match {
      case TimeUnit.MILLISECONDS => v
      case TimeUnit.SECONDS => v * 1000
      case TimeUnit.MINUTES => v * 1000 * 60
      case TimeUnit.HOURS => v * 1000 * 60 * 60
      case TimeUnit.DAYS => v * 60 * 60 * 24 * 1000
      case _ => v
    }


  override def update(sig: SignalEventFrame): Unit = {
    super.update(sig)
  }

  override def updateUnit(v: String): Unit = {
    super.updateUnit(v)
    timeUnit = sigUnit.map {
      case "ms" | "millis" | "milliseconds" => TimeUnit.MILLISECONDS
      case "s" | "sec" | "seconds" => TimeUnit.SECONDS
    } | timeUnit
  }


  override def toValuesData: Option[JsValue] =
    m.map { metric =>
      JsString(Seq(
        valueForLevels,
        metric.snapshot.getMean / 1000000,
        metric.snapshot.getStdDev / 1000000,
        metric.snapshot.get95thPercentile() / 1000000,
        metric.snapshot.get99thPercentile() / 1000000
      ).map(fmt).mkString(","))
    }

}

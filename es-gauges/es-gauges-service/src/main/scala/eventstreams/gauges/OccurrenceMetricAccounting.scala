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

import com.codahale.metrics.Meter
import play.api.libs.json.{JsString, JsValue}

trait OccurrenceMetricAccounting extends NumericMetricAccounting with WithMetric[Meter] {


  override def createMetric(metricName: String): Meter = metricRegistry.meter(metricName)

  override def updateValue(v: Double): Unit = {
    m.foreach { metric =>
      metric.mark(v.toLong)
      super.updateValue(metric.getOneMinuteRate)
    }
  }

  override def toValuesData: Option[JsValue] =
    m.map { metric =>
      JsString(Seq(
        valueForLevels,
        metric.getMeanRate,
        metric.getOneMinuteRate,
        metric.getFifteenMinuteRate,
        metric.getFifteenMinuteRate
      ).map(fmt).mkString(","))
    }

}

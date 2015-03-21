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

import nl.grons.metrics.scala.Histogram
import play.api.libs.json.{JsString, JsValue}

trait GaugeMetricAccounting extends NumericMetricAccounting with WithMetric[Histogram] {

  override def createMetric(metricName: String): Histogram = metrics.histogram(metricName)

  override def updateValue(v: Double): Unit = {
    super.updateValue(v)
    m.foreach(_ += v.toLong)
  }

  override def toValuesData: Option[JsValue] =
    m.map { metric =>
      JsString(Seq(
        valueForLevels,
        metric.snapshot.getMean,
        metric.snapshot.getStdDev,
        metric.snapshot.get95thPercentile(),
        metric.snapshot.get99thPercentile()
      ).map(fmt).mkString(","))
    }

}

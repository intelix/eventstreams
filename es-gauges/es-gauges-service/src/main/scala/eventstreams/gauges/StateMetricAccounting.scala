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

import eventstreams.signals.SignalEventFrame
import play.api.libs.json.{JsString, JsValue}

import scala.util.Try
import scala.util.matching.Regex


trait StateMetricAccounting extends MetricAccounting {

  private var lastVal: Option[String] = None

  private var levelRed: Option[Regex] = None
  private var levelYellow: Option[Regex] = None


  def valueForLevels: Option[String] =
    isSignalValueExpired match {
      case true => None
      case _ => lastVal
    }

  override def currentLevel: Int =
    valueForLevels match {
      case Some(v) if levelRed.isDefined && levelRed.get.findFirstIn(v).isDefined => LevelRed
      case Some(v) if levelYellow.isDefined && levelYellow.get.findFirstIn(v).isDefined => LevelYellow
      case Some(_) => LevelGreen
      case _ => LevelUnknown
    }

  private def toLevel(s: String): Option[Regex] = s match {
    case "_" | "" => None
    case v => Try (Some(v.r)).getOrElse(None)
  }

  def updateValue(v: String) = lastVal = Some(v)

  override def update(sig: SignalEventFrame): Unit = {
    super.update(sig)
    sig.sigValueString foreach updateValue
    sig.sigLevels foreach { v =>
      v.split(",").map(_.trim) match {
        case Array(y, r) =>
          levelRed = toLevel(r)
          levelYellow = toLevel(y)
      }
    }
  }

  def fmt(s: Option[String]) = s match {
    case Some(v) => v
    case _ => "_"
  }

  override def toValuesData: Option[JsValue] =
    Some(JsString(fmt(valueForLevels)))


}

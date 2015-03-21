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

import scala.util.Try


trait NumericMetricAccounting extends MetricAccounting {

  private var lastVal: Option[Double] = None

  private var levelLowerRed: Option[Double] = None
  private var levelLowerYellow: Option[Double] = None
  private var levelUpperYellow: Option[Double] = None
  private var levelUpperRed: Option[Double] = None



  def valueForLevels: Option[Double] =
    isSignalValueExpired match {
      case true => None
      case _ => lastVal
    }

  override def currentLevel: Int =
    valueForLevels match {
      case Some(v) if levelLowerRed.isDefined && levelLowerRed.get >= v => LevelRed
      case Some(v) if levelUpperRed.isDefined && levelUpperRed.get <= v => LevelRed
      case Some(v) if levelLowerYellow.isDefined && levelLowerYellow.get >= v => LevelYellow
      case Some(v) if levelUpperYellow.isDefined && levelUpperYellow.get <= v => LevelYellow
      case Some(_) => LevelGreen
      case _ => LevelUnknown
    }

  private def toLevel(s: String): Option[Double] = s match {
    case "_" | "" => None
    case v => Try(v.toDouble).map(Some(_)).getOrElse(None)
  }

  def updateValue(v: Double) = lastVal = Some(v)

  override def update(sig: SignalEventFrame): Unit = {
    super.update(sig)
    sig.sigValueNumeric foreach updateValue
    sig.sigLevels foreach { v =>
      v.split(",").map(_.trim) match {
        case Array(lr, ly, uy, ur) =>
          levelLowerRed = toLevel(lr)
          levelLowerYellow = toLevel(ly)
          levelUpperYellow = toLevel(uy)
          levelUpperRed = toLevel(ur)
      }
    }
  }

  def fmt(v: Any): String = v match {
    case x: Double => "%.2f" format x
    case Some(x: Double) => "%.2f" format x
    case _ => "_"
  }

}

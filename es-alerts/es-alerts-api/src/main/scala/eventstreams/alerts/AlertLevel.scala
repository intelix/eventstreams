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

package eventstreams.alerts

sealed trait AlertLevel {
  def code: Int
  def name: String
}

object AlertLevel {
  def default() = new AlertLevelVeryLow()
  def fromString(s: String) = s match {
    case "Very low" => AlertLevelVeryLow()
    case "Low" => AlertLevelLow()
    case "Medium" => AlertLevelMedium()
    case "High" => AlertLevelHigh()
    case "Very high" => AlertLevelVeryHigh()
    case "Maximum" => AlertLevelMaximum()
    case _ => AlertLevelVeryLow()
  }
}

case class AlertLevelVeryLow() extends AlertLevel {
  override val name = "Very low"
  override final def code: Int = 1
}

case class AlertLevelLow() extends AlertLevel {
  override val name = "Low"
  override final def code: Int = 3
}

case class AlertLevelMedium() extends AlertLevel {
  override val name = "Medium"
  override final def code: Int = 5
}

case class AlertLevelHigh() extends AlertLevel {
  override val name = "High"
  override final def code: Int = 7
}

case class AlertLevelVeryHigh() extends AlertLevel {
  override val name = "Very high"
  override final def code: Int = 9
}

case class AlertLevelMaximum() extends AlertLevel {
  override val name = "Maximum"
  override final def code: Int = 10
}


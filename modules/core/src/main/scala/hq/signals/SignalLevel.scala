package hq.signals

sealed trait SignalLevel {
  def code: Int
  def name: String
}

object SignalLevel {
  def default() = new SignalLevelVeryLow()
  def fromString(s: String) = s match {
    case "Very low" => SignalLevelVeryLow()
    case "Low" => SignalLevelLow()
    case "Medium" => SignalLevelMedium()
    case "High" => SignalLevelHigh()
    case "Very high" => SignalLevelVeryHigh()
    case "Maximum" => SignalLevelMaximum()
    case _ => SignalLevelVeryLow()
  }
}

case class SignalLevelVeryLow() extends SignalLevel {
  override val name = "Very low"
  override final def code: Int = 1
}

case class SignalLevelLow() extends SignalLevel {
  override val name = "Low"
  override final def code: Int = 3
}

case class SignalLevelMedium() extends SignalLevel {
  override val name = "Medium"
  override final def code: Int = 5
}

case class SignalLevelHigh() extends SignalLevel {
  override val name = "High"
  override final def code: Int = 7
}

case class SignalLevelVeryHigh() extends SignalLevel {
  override val name = "Very high"
  override final def code: Int = 9
}

case class SignalLevelMaximum() extends SignalLevel {
  override val name = "Maximum"
  override final def code: Int = 10
}


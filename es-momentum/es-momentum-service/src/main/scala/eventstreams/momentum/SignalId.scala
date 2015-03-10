package eventstreams.momentum

import eventstreams.signals.SignalEventFrame

private case class SignalId(sensor: String, location: Option[String], group: Option[String], subgroup: Option[String]) {
  lazy val toMetricName: String = (group.toList ++ subgroup.toList ++ List(sensor) ++ location.toList).mkString(".")

  def matchesQuery(q: String) = q match {
    case "all" | "*" => true
    case s if s.endsWith("*") && s.startsWith("*") => toMetricName.contains(s.substring(1, s.length - 1))
    case s if s.startsWith("*") => toMetricName.endsWith(s.substring(1))
    case s if s.endsWith("*") => toMetricName.endsWith(s.substring(0, s.length - 1))
    case s => s == toMetricName
  }
}


private object SignalId {
  def apply(sensor: String, s: SignalEventFrame) = new SignalId(sensor, s.location, s.group, s.subgroup)
}
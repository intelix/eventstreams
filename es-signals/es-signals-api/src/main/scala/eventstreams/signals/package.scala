package eventstreams

import scala.language.implicitConversions

package object signals {

  sealed trait SigClass

  case class SigClassCritical() extends SigClass

  case class SigClassEssential() extends SigClass

  case class SigClassInfo() extends SigClass

  case class SigClassNonImportant() extends SigClass

  sealed trait SigMetric[T] {
    def id: String

    def valueFrom(s: SignalEventFrame): Option[T]
    def valueFrom(e: EventData): Option[T]
  }

  trait NumericMetric extends SigMetric[Double] {
    override def valueFrom(s: SignalEventFrame): Option[Double] = s.valDouble
    override def valueFrom(e: EventData): Option[Double] = e.asNumber.map(_.doubleValue())
  }

  case class SigMetricGauge() extends NumericMetric {
    override def id: String = "g"
  }

  case class SigMetricTiming() extends NumericMetric {
    override def id: String = "t"
  }

  case class SigMetricOccurrence() extends NumericMetric {
    override def id: String = "o"
  }

  case class SigMetricState() extends NumericMetric {
    override def id: String = "s"
  }


  trait Signal {

    protected def evt: EventFrame

    def location = evt ~> 'loc

    def group = evt ~> 'grp

    def subgroup = evt ~> 'grp2

    def sensor = evt ~> 'sen

    def sclass: Option[SigClass] = (evt ~> 'cls).map {
      case "c" | "critical" | "3" => SigClassCritical()
      case "e" | "essential" | "2" => SigClassEssential()
      case "i" | "info" | "1" => SigClassInfo()
      case _ => SigClassNonImportant()
    }

    def metric: Option[SigMetric[_]] = (evt ~> 'metr).collect {
      case "g" | "gauge" => SigMetricGauge()
      case "t" | "timing" => SigMetricTiming()
      case "o" | "occurrence" => SigMetricOccurrence()
      case "s" | "state" => SigMetricState()
    }

    def ts = evt ++> 'date_ts

    def ttl = evt ++> 'sttl

    def valLong = evt ++> 'valn

    def valInt = evt +> 'valn

    def valDouble = evt +&> 'valn

    def valString = evt ~> 'vals

    def unit = evt ~> 'unit
    def samplingRate = evt +&> 'srt
    def ranges = evt ##> 'rng
    def levels = evt ##> 'lvl


  }

  class SignalEventFrame(protected val evt: EventFrame) extends Signal

  implicit def eventToSignal(f: EventFrame): SignalEventFrame = new SignalEventFrame(f)

}

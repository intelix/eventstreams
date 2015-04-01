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

package eventstreams

import eventstreams.EventFrameConverter.optionsConverter

import scala.language.implicitConversions
import scalaz.Scalaz._

package object signals {

  sealed trait SigMetricType {
    def id: String
  }

  case class SigMetricTypeGauge() extends SigMetricType {
    override def id: String = SignalConst.MetricTypeGauge
  }

  case class SigMetricTypeTiming() extends SigMetricType {
    override def id: String = SignalConst.MetricTypeTiming
  }

  case class SigMetricTypeOccurrence() extends SigMetricType {
    override def id: String = SignalConst.MetricTypeOccurrence
  }

  case class SigMetricTypeState() extends SigMetricType {
    override def id: String = SignalConst.MetricTypeState
  }


  object SignalConst {
    val Unspecified = "_"
    val SegmentsSeparator = "~"
    val DefaultPriority = "M"

    val nameSetter = EventValuePath("sig.n")

    val fieldSig = "sig"
    val fieldSigPriority = "p"
    val fieldSigName = "a"
    val fieldSigLocation = "L"
    val fieldSigSystem = "S"
    val fieldSigComponent = "C"
    val fieldSigMetric = "M"
    val fieldSigMetricType = "t"
    val fieldSigStrValue = "s"
    val fieldSigNumValue = "n"
    val fieldSigTimestamp = "t"
    val fieldSigTTL = "l"
    val fieldSigUnit = "u"
    val fieldSigSampleRate = "r"
    val fieldSigRanges = "a"
    val fieldSigLevels = "e"


    val MetricTypeGauge = "g"
    val MetricTypeTiming = "t"
    val MetricTypeOccurrence = "m"
    val MetricTypeState = "s"

  }

  object SignalKeyBuilder {

    import SignalConst._

    def apply(name: String): Option[SignalKey] =
      name.split(SegmentsSeparator).toSeq.map {
        case Unspecified => None
        case x => Some(x)
      } match {
        case Seq(Some(d)) => Some(SignalKey(None, None, None, d))
        case Seq(c, Some(d)) => Some(SignalKey(None, None, c, d))
        case Seq(b, c, Some(d)) => Some(SignalKey(None, b, c, d))
        case Seq(a, b, c, Some(d)) => Some(SignalKey(a, b, c, d))
        case Seq(a, b, c, Some(d), tail@_*) => Some(SignalKey(a, b, c, (Seq(d) ++ tail.flatten).mkString("/")))
        case _ => None
      }
  }

  case class SignalKey(location: Option[String], system: Option[String], component: Option[String], metric: String) {

    import SignalConst._

    lazy val toMetricName: String = Seq(location | Unspecified, system | Unspecified, component | Unspecified, metric).mkString("~")

    def matchesQuery(q: String) = q match {
      case "all" | "*" => true
      case s if s.endsWith("*") && s.startsWith("*") => toMetricName.contains(s.substring(1, s.length - 1))
      case s if s.startsWith("*") => toMetricName.endsWith(s.substring(1))
      case s if s.endsWith("*") => toMetricName.endsWith(s.substring(0, s.length - 1))
      case s => s == toMetricName
    }

    lazy val locationAsSeq: Seq[String] = location.map(toSeq) | Seq(Unspecified)
    lazy val systemAsSeq: Seq[String] = system.map(toSeq) | Seq(Unspecified)
    lazy val componentAsSeq: Seq[String] = component.map(toSeq) | Seq(Unspecified)
    lazy val metricAsSeq: Seq[String] = toSeq(metric)

    private def toSeq(s: String) =
      s.split('.').filterNot(_.isEmpty).toSeq match {
        case b if b.isEmpty => Seq(Unspecified)
        case b => b
      }
  }

  trait Signal {

    import SignalConst._

    protected def evt: EventFrame

    private lazy val signal = evt #> fieldSig

    lazy val sigKey = sigMetric.map(SignalKey(sigLocation, sigSystem, sigComponent, _))

    lazy val sigName: Option[String] = signal ~> fieldSigName

    private lazy val sigKeyFromName = sigName.flatMap(SignalKeyBuilder(_))

    lazy val sigLocation = signal ~> fieldSigLocation orElse sigKeyFromName.flatMap(_.location)

    lazy val sigSystem = signal ~> fieldSigSystem orElse sigKeyFromName.flatMap(_.system)

    lazy val sigComponent = signal ~> fieldSigComponent orElse sigKeyFromName.flatMap(_.component)

    lazy val sigMetric = signal ~> fieldSigMetric orElse sigKeyFromName.map(_.metric)

    lazy val sigPriority = signal ~> fieldSigPriority | DefaultPriority


    lazy val sigMetricType: Option[SigMetricType] = (signal ~> fieldSigMetricType).collect {
      case MetricTypeGauge => SigMetricTypeGauge()
      case MetricTypeTiming => SigMetricTypeTiming()
      case MetricTypeOccurrence => SigMetricTypeOccurrence()
      case _ => SigMetricTypeState()
    }

    lazy val sigTs = signal ++> fieldSigTimestamp orElse evt ++> 'date_ts

    lazy val sigTTLMs = signal ++> fieldSigTTL

    lazy val sigValueNumeric = signal +&> fieldSigNumValue
    lazy val sigValueString = signal ~> fieldSigStrValue orElse sigValueNumeric.map("%.2f" format _)

    lazy val sigUnit = signal ~> fieldSigUnit
    lazy val sigSamplingRateMs = signal +> fieldSigSampleRate
    lazy val sigRanges = signal ~> fieldSigRanges
    lazy val sigLevels = signal ~> fieldSigLevels


  }

  class SignalEventFrame(val evt: EventFrame) extends Signal {
    def this( streamKey: String,
              streamSeed: String,
              metric: Option[String] = None,
              strValue: Option[String] = None,
              numValue: Option[Double] = None,
              name: Option[String] = None,
              location: Option[String] = None,
              system: Option[String] = None,
              comp: Option[String] = None,
              metricType: Option[SigMetricType] = None,
              priority: Option[String] = None,
              timestamp: Option[Long] = None,
              ttlMs: Option[Long] = None,
              unit: Option[String] = None,
              samplingRateMs: Option[Long] = None,
              ranges: Option[String] = None,
              levels: Option[String] = None
              ) =
      this(
        EventFrame(
          "streamKey" -> streamKey,
          "streamSeed" -> streamSeed,
          SignalConst.fieldSig -> EventFrame(
            name.map(SignalConst.fieldSigName -> _).toList ++
              location.map(SignalConst.fieldSigLocation -> _).toList ++
              system.map(SignalConst.fieldSigSystem -> _).toList ++
              comp.map(SignalConst.fieldSigComponent -> _).toList ++
              metricType.map(SignalConst.fieldSigMetricType -> _.id).toList ++
              priority.map(SignalConst.fieldSigPriority -> _).toList ++
              strValue.map(SignalConst.fieldSigStrValue -> _).toList ++
              numValue.map(SignalConst.fieldSigNumValue -> _).toList ++
              timestamp.map(SignalConst.fieldSigTimestamp -> _).toList ++
              ttlMs.map(SignalConst.fieldSigTTL -> _).toList ++
              unit.map(SignalConst.fieldSigUnit -> _).toList ++
              samplingRateMs.map(SignalConst.fieldSigSampleRate -> _).toList ++
              ranges.map(SignalConst.fieldSigRanges -> _).toList ++
              levels.map(SignalConst.fieldSigLevels -> _).toList
              : _*
          )
        )
      )
  }

  implicit def eventToSignal(f: EventFrame): SignalEventFrame = new SignalEventFrame(f)
  implicit def signalToEvent(f: SignalEventFrame): EventFrame = f.evt

}

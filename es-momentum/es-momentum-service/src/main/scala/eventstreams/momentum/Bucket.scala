package eventstreams.momentum

import java.util.concurrent.TimeUnit

import eventstreams.signals._
import eventstreams.{EventData, WithMetrics}

import scalaz.Scalaz._

sealed trait WarningLevel {
  def id: Int
}

case class WarningLevelGreen() extends WarningLevel {
  override def id: Int = 0
}

case class WarningLevelYellow() extends WarningLevel {
  override def id: Int = 1
}

case class WarningLevelRed() extends WarningLevel {
  override def id: Int = 2
}

private case class LevelsConfig(yellow: Double, red: Double) {
  def levelFor(v: Double): WarningLevel = v match {
    case x if x == yellow => WarningLevelYellow()
    case x if x == red => WarningLevelRed()
    case x if yellow.compareTo(red) <= 0 =>
      if (x.compareTo(yellow) < 0) WarningLevelGreen()
      else if (x.compareTo(red) >= 0) WarningLevelRed()
      else WarningLevelYellow()
    case x if yellow.compareTo(red) > 0 =>
      if (x.compareTo(yellow) > 0) WarningLevelGreen()
      else if (x.compareTo(red) <= 0) WarningLevelRed()
      else WarningLevelYellow()
  }
}

private[momentum] trait Bucket {
  val id: SignalId
  val uid: Long

  val metric: SigMetric[Double]

  var lastReading: Option[Double] = None

  var levels: Option[LevelsConfig] = None
  var unit: Option[String] = None
  var samplingRate: Option[Double] = None

  def ranges: String

  def updateReading(s: SignalEventFrame): Boolean =
    metric.valueFrom(s).map { v =>
      updateValue(v)
      true
    } | false


  def updateMeta(s: SignalEventFrame): Boolean = {
    var updated = false

    s.unit foreach { v =>
      updateUnit(v)
      updated = true
    }
    s.samplingRate foreach { v =>
      updateSamplingRate(v)
      updated = true
    }
    s.ranges foreach { v =>
      updateRanges(v)
      updated = true
    }
    s.levels foreach { v =>
      updateLevels(v)
      updated = true
    }

    updated
  }

  def currentValues: Seq[Double]

  protected def account(v: Double): Double

  protected def updateValue(v: Double): Unit = lastReading = Some(account(v))

  protected def updateLevels(v: Seq[EventData]): Unit =
    v match {
      case Seq(y, r) => for (
        yv <- metric.valueFrom(y);
        rv <- metric.valueFrom(r)
      ) yield LevelsConfig(yv, rv)
    }

  protected def updateUnit(v: String) = unit = Some(v)

  protected def updateSamplingRate(v: Double) = samplingRate = Some(v)

  protected def updateRanges(v: Seq[EventData]): Unit
}

object BucketBuilder {
  def apply(uid: Long, id: SignalId, s: SignalEventFrame): Option[Bucket] = s.metric.collect {
    case SigMetricOccurrence() => OccurrenceBucket(uid, id)
    case SigMetricGauge() => GaugeBucket(uid, id)
    case SigMetricState() => StateBucket(uid, id)
    case SigMetricTiming() => TimingBucket(uid, id)
  }
}

trait NumericMinMaxRange {
  var rangeMin: Option[Double] = None
  var rangeMax: Option[Double] = None

  def ranges: String = (rangeMin | 0) + "," + (rangeMax | 0)

  def updateRanges(v: Seq[EventData]): Unit =
    v match {
      case Seq(min, max) =>
        rangeMin = min.asNumber.map(_.doubleValue())
        rangeMax = max.asNumber.map(_.doubleValue())
      case Seq(max) =>
        rangeMax = max.asNumber.map(_.doubleValue())
      case _ => ()
    }
}

private case class GaugeBucket(uid: Long, id: SignalId) extends Bucket with NumericMinMaxRange with WithMetrics {
  private val m = metrics.histogram(id.toMetricName)

  override protected def account(v: Double): Double = {
    m += v.toLong
    v
  }

  override val metric: SigMetric[Double] = SigMetricGauge()

  override def currentValues: Seq[Double] =
    lastReading.map(
      Seq[Double](_,
        m.snapshot.getMin,
        m.snapshot.getMax,
        m.snapshot.getMean,
        m.snapshot.getMedian,
        m.snapshot.get95thPercentile(),
        m.snapshot.get99thPercentile())) | Seq()
}

private case class TimingBucket(uid: Long, id: SignalId) extends Bucket with NumericMinMaxRange with WithMetrics {
  private val m = metrics.timer(id.toMetricName)

  override protected def account(v: Double): Double = {
    timeUnit match {
      case TimeUnit.SECONDS => m.update((v * 1000).toLong, TimeUnit.MILLISECONDS)
      case TimeUnit.MINUTES => m.update((v * 1000 * 60).toLong, TimeUnit.MILLISECONDS)
      case TimeUnit.HOURS => m.update((v * 1000 * 60 * 60).toLong, TimeUnit.MILLISECONDS)
      case TimeUnit.DAYS => m.update((v * 60 * 60 * 24).toLong, TimeUnit.SECONDS)
      case t => m.update(v.toLong, t)
    }
    v
  }

  override val metric: SigMetric[Double] = SigMetricTiming()

  private var timeUnit: TimeUnit = TimeUnit.MILLISECONDS

  override protected def updateUnit(v: String): Unit = {
    super.updateUnit(v)
    timeUnit = unit.map {
      case "ms" | "millis" | "milliseconds" => TimeUnit.MILLISECONDS
      case "s" | "sec" | "seconds" => TimeUnit.SECONDS
    } | timeUnit
  }

  override def currentValues: Seq[Double] =
    lastReading.map(
      Seq[Double](_,
        m.meanRate,
        m.oneMinuteRate,
        m.fiveMinuteRate,
        m.fifteenMinuteRate,
        m.snapshot.getMin/1000000,
        m.snapshot.getMax/1000000,
        m.snapshot.getMean/1000000,
        m.snapshot.getMedian/1000000,
        m.snapshot.get95thPercentile()/1000000,
        m.snapshot.get99thPercentile()/1000000)) | Seq()

}

private case class OccurrenceBucket(uid: Long, id: SignalId) extends Bucket with NumericMinMaxRange with WithMetrics {
  private val m = metrics.meter(id.toMetricName)

  override protected def account(v: Double): Double = {
    m.mark(v.toLong)
    v
  }

  override val metric: SigMetric[Double] = SigMetricOccurrence()

  override def currentValues: Seq[Double] =
    lastReading.map(
      Seq[Double](_,
        m.meanRate,
        m.oneMinuteRate,
        m.fiveMinuteRate,
        m.fifteenMinuteRate)) | Seq()
}

private case class StateBucket(uid: Long, id: SignalId) extends Bucket {
  override protected def account(v: Double): Double = v

  override val metric: SigMetric[Double] = SigMetricState()

  override def currentValues: Seq[Double] =
    lastReading.map(
      Seq[Double](_)) | Seq()

  override def ranges: String = ""

  override protected def updateRanges(v: Seq[EventData]): Unit = {}
}

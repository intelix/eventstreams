package eventstreams.core.metrics

import java.util.concurrent.TimeUnit

import nl.grons.metrics.scala.Timer

trait TimerSensor {
  type T
  def updateMs(ms: Long) = {}
  def updateNs(ms: Long) = {}
  def withTimer(f: => T): T = f
}

object TimerSensor {
  val Disabled = new TimerSensor {}
  def apply(id: String, create: String => Timer) = new TimerSensorImpl(id, create)
}


class TimerSensorImpl(val id: String, private val create: String => Timer)  extends TimerSensor with Sensor {

  private lazy val m = create(id)

  override def withTimer(f: => T): T = m.time[T](f)

  override def updateMs(ms: Long) = m.update(ms, TimeUnit.MILLISECONDS)
  override def updateNs(ms: Long) = m.update(ms, TimeUnit.NANOSECONDS)

}

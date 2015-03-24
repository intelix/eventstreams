package eventstreams.core.metrics

import nl.grons.metrics.scala.Histogram

trait HistogramSensor {
  def update(value : scala.Long) = {}
  def update(value : scala.Int) = {}
}

object HistogramSensor {
  val Disabled = new HistogramSensor {}
  def apply(id: String, create: String => Histogram) = new HistogramSensorImpl(id, create)
}



class HistogramSensorImpl(val id: String, private val create: String => Histogram)  extends HistogramSensor with Sensor {

  private lazy val m = create(id)

  override def update(value : scala.Long) = m += value
  override def update(value : scala.Int) = m += value

}

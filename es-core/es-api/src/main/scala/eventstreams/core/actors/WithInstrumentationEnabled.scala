package eventstreams.core.actors

import eventstreams.WithCHMetrics
import eventstreams.core.metrics.EventstreamsSensorRegistry.{registerSharedSensor, unregisterSharedSensor}
import eventstreams.core.metrics._

import scalaz.Scalaz._

trait WithInstrumentationEnabled extends WithInstrumentationHooks with WithCHMetrics {

  private lazy val prefix =
    (sensorHostId.map("h:" + _).toList ++
      sensorSystemId.map("s:" + _) ++
      sensorComponentId.map("c:" + _) ++
      Seq("m:")) mkString "~"

  private var localSensors: Map[String, Sensor] = Map()

  final override def destroySensors() = {
    localSensors.keys.foreach(destroySensor)
    localSensors = Map()
  }

  def sensorHostId: Option[String]

  def sensorSystemId: Option[String]

  def sensorComponentId: Option[String]

  override def meterSensor(group: Option[String], name: String) = locateOrCreate(group, name, id => MeterSensor(id, metricRegistry.meter))

  override def histogramSensor(group: Option[String], name: String) = locateOrCreate(group, name, id => HistogramSensor(id, metricRegistry.histogram))

  override def timerSensor(group: Option[String], name: String) = locateOrCreate(group, name, id => TimerSensor(id, metricRegistry.timer))

  override def stateSensor(group: Option[String], name: String) = locateOrCreate(group, name, id => StateSensor(id, SimpleStateRegistry.getPublisherFor))

  private def locateOrCreate[T <: Sensor](group: Option[String], name: String, create: String => T) = {
    val fullName = fullSensorName(group, name)
    localSensors.get(fullName) match {
      case Some(m) => m.asInstanceOf[T]
      case None =>
        registerSharedSensor(fullName)
        val m = create(fullName)
        localSensors += fullName -> m
        m
    }
  }

  private def fullSensorName(group: Option[String], metric: String) = prefix + (group.map(_ + ".") | "") + metric

  private def destroySensor(id: String) = {
    unregisterSharedSensor(id)
    SimpleStateRegistry.remove(id)
  }

}

package eventstreams.core.metrics

import eventstreams.WithCHMetrics

object EventstreamsSensorRegistry extends WithCHMetrics {
  var sharedSensors: Map[String, Int] = Map()

  def registerSharedSensor(id: String) = {
    println(s"!>>> Registering $id")

    sharedSensors += id -> (sharedSensors.getOrElse(id, 0) + 1)
  }

  def unregisterSharedSensor(id: String) = {
    println(s"!>>> Unregistering $id")
    sharedSensors.get(id) match {
      case Some(i) if i > 1 => sharedSensors += id -> (i - 1)
      case Some(_) =>
        sharedSensors -= id
        metricRegistry.remove(id)
      case _ => ()
    }
  }

}

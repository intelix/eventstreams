package eventstreams.core.actors

import core.sysevents.{SyseventComponent, SyseventSystemRef}
import eventstreams.WithCHMetrics
import eventstreams.core.actors.ext.EventstreamsEnvironment

trait ActorWithInstrumentationEnabled extends ActorUtils with WithInstrumentationEnabled with SyseventComponent {

  private val env = EventstreamsEnvironment(context.system)

  override def sensorHostId: Option[String] = env.NodeId

  override def sensorSystemId: Option[String] = Some(SyseventSystemRef.ref.id)

  override def sensorComponentId: Option[String] = Some(componentId)

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    self ! EnableInstrumentation(sensorHostId, sensorSystemId, sensorComponentId)
    super.preStart()
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    destroySensors()
    super.postStop()
  }
}

package eventstreams.flows

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{BaseActorSysevents, PipelineWithStatesActor, StateChangeSysevents}


trait FlowProxySysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val FlowStarted = 'FlowStarted.trace
  val FlowConfigured = 'FlowConfigured.trace

  override def componentId: String = "Flow.Flow"
}
object FlowProxySysevents extends FlowProxySysevents


class FlowProxyActor
  extends PipelineWithStatesActor
  with FlowActorSysevents
  with WithSyseventPublisher {

}

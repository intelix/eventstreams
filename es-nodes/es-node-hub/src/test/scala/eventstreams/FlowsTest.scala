package eventstreams

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.flows.internal.{BlackholeAutoAckSinkActor, GateInputActor}
import eventstreams.flows.{FlowDeployableSysevents, FlowDeployerActor, FlowManagerActor, FlowProxyActor}
import eventstreams.instructions.{EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class FlowsTest extends FlatSpec with HubNodeTestContext with WorkerNodeTestContext with SharedActorSystem with LogInstructionConstants with EnrichInstructionConstants {

  trait WithFlowManager extends WithHubNode1 with WithWorkerNode1 {
    startFlowManager(hub1System)
    val flowManagerComponentKey = ComponentKey(FlowManagerActor.id)
    expectOneOrMoreEvents(MessageRouterActor.RouteAdded, 'Route -> FlowManagerActor.id)
  }

  "FlowManager" should "start" in new WithHubNode1 {
    startFlowManager(hub1System)
    expectOneOrMoreEvents(FlowManagerActor.PreStart)
  }

  it should "request all stored flows from storage" in new WithHubNode1 {
    startFlowManager(hub1System)
    expectOneOrMoreEvents(ConfigStorageActor.RequestedAllMatchingEntries, 'PartialKey -> "flows/")
  }

  it should "respond to list subscription with an empty payload" in new WithFlowManager {
    startMessageSubscriber1(hub1System)
    subscribeFrom1(hub1System, LocalSubj(flowManagerComponentKey, T_LIST))
    expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "[]")
  }

  it should "respond to configtpl subscription" in new WithFlowManager {
    startMessageSubscriber1(hub1System)
    subscribeFrom1(hub1System, LocalSubj(flowManagerComponentKey, T_CONFIGTPL))
    expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "Flow configuration".r)
  }


  val validFlow1 = Json.obj(
    "name" -> "flow1",
    "sourceGateName" -> (Hub1Address + "/user/gate1"),
    "deployTo" -> "hubworker",
    "pipeline" -> Json.arr(
      Json.obj(
        CfgFClass -> "enrich",
        CfgFFieldToEnrich -> "abc",
        CfgFTargetValueTemplate -> "${abc1}",
        CfgFTargetType -> "s"),
      Json.obj(
        CfgFClass -> "log",
        CfgFEvent -> "eventname",
        CfgFLevel -> "INFO"
      )
    )
  )

  it should "create a flow actor in response to add command" in new WithFlowManager {
    startMessageSubscriber1(hub1System)
    commandFrom1(hub1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(validFlow1))
    expectOneOrMoreEvents(FlowProxyActor.PreStart)
    expectOneOrMoreEvents(FlowProxyActor.FlowConfigured, 'Name -> "flow1")
  }

  trait WithFlow1Created extends WithFlowManager {
    startMessageSubscriber1(hub1System)
    startGate1("gate1")
    commandFrom1(hub1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(validFlow1))
    expectOneOrMoreEvents(FlowProxyActor.PreStart)
    expectOneOrMoreEvents(FlowProxyActor.FlowConfigured, 'Name -> "flow1")
    val flow1ComponentKey = ComponentKey(locateLastEventFieldValue(FlowProxyActor.FlowConfigured, "ID").asInstanceOf[String])
  }

  "Flow" should "start in response to start command" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
  }

  it should "register with the gate" in new WithFlow1Created {
    expectOneOrMoreEvents(GateStubActor.RegisterSinkReceived)
  }

  it should "process event" taggedAs OnlyThisTest in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
    expectExactlyNEvents(1, GateStubActor.RegisterSinkReceived)
    expectExactlyNEvents(1, FlowDeployableSysevents.BecomingActive)
    clearEvents()
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> "1", "streamKey" -> "key", "streamSeed" -> "1", "abc1" -> "xyz"), 123))
    expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc1":"xyz"""".r)

    expectExactlyNEvents(1, FlowDeployableSysevents.ForwardedToFlow, 'InstanceId -> s"1@$worker1Address")
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 123)

    collectAndPrintEvents()
  }

  it should "terminate in response to remove command" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
    clearEvents()
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_REMOVE), None)
    expectOneOrMoreEvents(GateInputActor.PostStop)
  }


}

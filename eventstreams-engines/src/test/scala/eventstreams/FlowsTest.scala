package eventstreams

import eventstreams.core.JsonFrame
import eventstreams.core.agent.core.Acknowledgeable
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{ComponentKey, LocalSubj}
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.engine.flows.core.{GateInputActor, BlackholeAutoAckSinkActor}
import eventstreams.engine.flows.{FlowActor, FlowManagerActor}
import eventstreams.plugins.essentials.{EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support.{GateStubActor, EngineNodeTestContext, SharedActorSystem, SubscribingComponentStub}
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class FlowsTest extends FlatSpec with EngineNodeTestContext with SharedActorSystem with LogInstructionConstants with EnrichInstructionConstants {

  trait WithFlowManager extends WithEngineNode1 {
    startFlowManager(engine1System)
    val flowManagerComponentKey = ComponentKey(FlowManagerActor.id)
    expectSomeEvents(MessageRouterActor.RouteAdded, 'Route -> FlowManagerActor.id)
  }

  "FlowManager" should "start" in new WithEngineNode1  {
    startFlowManager(engine1System)
    expectSomeEvents(FlowManagerActor.PreStart)
  }

  it should "request all stored flows from storage" in new WithEngineNode1  {
    startFlowManager(engine1System)
    expectSomeEvents(ConfigStorageActor.RequestedAllMatchingEntries, 'PartialKey -> "flow/")
  }

  it should "respond to list subscription with an empty payload" in new WithFlowManager  {
    startMessageSubscriber1(engine1System)
    subscribeFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_LIST))
    expectSomeEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "[]")
  }

  it should "respond to configtpl subscription" in new WithFlowManager  {
    startMessageSubscriber1(engine1System)
    subscribeFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_CONFIGTPL))
    expectSomeEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "Flow configuration".r)
  }


  val validFlow1 = Json.obj(
    "name" -> "flow1",
    "sourceGateName" -> "/user/gate1",
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

  it should "create a flow actor in response to add command" in new WithFlowManager  {
    startMessageSubscriber1(engine1System)
    commandFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(validFlow1))
    expectSomeEvents(FlowActor.PreStart)
    expectSomeEvents(FlowActor.FlowConfigured, 'Name -> "flow1")
  }

  trait WithFlow1Created extends WithFlowManager {
    startMessageSubscriber1(engine1System)
    startGate1("gate1")
    commandFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(validFlow1))
    expectSomeEvents(FlowActor.PreStart)
    expectSomeEvents(FlowActor.FlowConfigured, 'Name -> "flow1")
    val flow1ComponentKey = ComponentKey(locateLastEventFieldValue(FlowActor.FlowConfigured, "ID").asInstanceOf[String])
  }

  "Flow" should "start in response to start command" in new WithFlow1Created  {
    commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEvents(1, FlowActor.BecomingActive)
  }

  it should "register with the gate" in new WithFlow1Created  {
    expectSomeEvents(1, GateStubActor.RegisterSinkReceived)
  }

  it should "process event" in new WithFlow1Created  {
    commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEvents(1, FlowActor.BecomingActive)
    clearEvents()
    sendFromGateToSinks("gate1", Acknowledgeable(JsonFrame(Json.obj("eventId"->"1", "abc1" -> "xyz"), Map()), 123))
    expectSomeEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """{"eventId":"1","abc":"xyz","abc1":"xyz"}""")
  }

  it should "terminate in response to kill command" in new WithFlow1Created  {
    commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEvents(1, FlowActor.BecomingActive)
    clearEvents()
    commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_KILL), None)
    expectSomeEvents(GateInputActor.PostStop)
  }


}

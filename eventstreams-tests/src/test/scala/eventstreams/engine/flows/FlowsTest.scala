package eventstreams.engine.flows

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{ComponentKey, LocalSubj}
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.support.{SharedActorSystem, SubscribingComponentStub, IsolatedActorSystems, MultiNodeTestingSupport}
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class FlowsTest extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {

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

  it should "create a flow actor in response to add command" in new WithFlowManager  {
    startMessageSubscriber1(engine1System)
    commandFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(Json.obj("name" -> "flow1")))
    expectSomeEvents(FlowActor.PreStart)
    expectSomeEvents(FlowActor.FlowConfigured, 'Name -> "flow1")
    collectAndPrintEvents()
  }


}

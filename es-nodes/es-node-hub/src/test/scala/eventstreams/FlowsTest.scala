package eventstreams

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.flows.internal.{BlackholeAutoAckSinkActor, GateInputActor}
import eventstreams.flows.{FlowActor, FlowManagerActor}
import eventstreams.instructions.{EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support.{EngineNodeTestContext, GateStubActor, SharedActorSystem, SubscribingComponentStub}
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class FlowsTest extends FlatSpec with EngineNodeTestContext with SharedActorSystem with LogInstructionConstants with EnrichInstructionConstants {

   trait WithFlowManager extends WithEngineNode1 {
     startFlowManager(engine1System)
     val flowManagerComponentKey = ComponentKey(FlowManagerActor.id)
     expectOneOrMoreEvents(MessageRouterActor.RouteAdded, 'Route -> FlowManagerActor.id)
   }

   "FlowManager" should "start" in new WithEngineNode1  {
     startFlowManager(engine1System)
     expectOneOrMoreEvents(FlowManagerActor.PreStart)
   }

   it should "request all stored flows from storage" in new WithEngineNode1  {
     startFlowManager(engine1System)
     expectOneOrMoreEvents(ConfigStorageActor.RequestedAllMatchingEntries, 'PartialKey -> "flow/")
   }

   it should "respond to list subscription with an empty payload" in new WithFlowManager  {
     startMessageSubscriber1(engine1System)
     subscribeFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_LIST))
     expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "[]")
   }

   it should "respond to configtpl subscription" in new WithFlowManager  {
     startMessageSubscriber1(engine1System)
     subscribeFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_CONFIGTPL))
     expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Data -> "Flow configuration".r)
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
     expectOneOrMoreEvents(FlowActor.PreStart)
     expectOneOrMoreEvents(FlowActor.FlowConfigured, 'Name -> "flow1")
   }

   trait WithFlow1Created extends WithFlowManager {
     startMessageSubscriber1(engine1System)
     startGate1("gate1")
     commandFrom1(engine1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(validFlow1))
     expectOneOrMoreEvents(FlowActor.PreStart)
     expectOneOrMoreEvents(FlowActor.FlowConfigured, 'Name -> "flow1")
     val flow1ComponentKey = ComponentKey(locateLastEventFieldValue(FlowActor.FlowConfigured, "ID").asInstanceOf[String])
   }

   "Flow" should "start in response to start command" in new WithFlow1Created  {
     commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
     expectExactlyNEvents(1, FlowActor.BecomingActive)
   }

   it should "register with the gate" in new WithFlow1Created  {
     expectOneOrMoreEvents(GateStubActor.RegisterSinkReceived)
   }

   it should "process event" in new WithFlow1Created  {
     commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
     expectExactlyNEvents(1, FlowActor.BecomingActive)
     clearEvents()
     sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId"->"1", "abc1" -> "xyz"), 123))
     expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc1":"xyz"""".r)
   }

   it should "terminate in response to remove command" in new WithFlow1Created  {
     commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_START), None)
     expectExactlyNEvents(1, FlowActor.BecomingActive)
     clearEvents()
     commandFrom1(engine1System, LocalSubj(flow1ComponentKey, T_REMOVE), None)
     expectOneOrMoreEvents(GateInputActor.PostStop)
   }


 }

package eventstreams

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.flows.internal.{BlackholeAutoAckSinkActor, PassiveInputActor}
import eventstreams.flows.{FlowDeployableSysevents, FlowDeployerActor, FlowManagerActor, FlowProxyActor}
import eventstreams.instructions.{EnrichInstructionConstants, LogInstructionConstants}
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json


class FlowsTest extends FlatSpec with HubNodeTestContext with WorkerNodeTestContext with IsolatedActorSystems with LogInstructionConstants with EnrichInstructionConstants {

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
    "instancesPerNode" -> 2,
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
    var flowConfig = validFlow1
    startMessageSubscriber1(hub1System)
    startGate1("gate1")
    commandFrom1(hub1System, LocalSubj(flowManagerComponentKey, T_ADD), Some(flowConfig))
    expectOneOrMoreEvents(FlowProxyActor.PreStart)
    expectOneOrMoreEvents(FlowProxyActor.FlowConfigured, 'Name -> "flow1")
    val flow1ComponentKey = ComponentKey(locateLastEventFieldValue(FlowProxyActor.FlowConfigured, "ID").asInstanceOf[String])
  }

  "Flow proxy" should "start in response to start command" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
  }


  "Started flow proxy" should "register with the gate" in new WithFlow1Created {
    expectOneOrMoreEvents(GateStubActor.RegisterSinkReceived)
  }


  it should "deploy flow instance into host with required role" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 1, 'ActiveWorkers -> 2)
  }

  it should "start deployed flow instances" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEventsWithTimeout(30000, 2, FlowDeployableSysevents.FlowStarted)
  }

  it should "stop itself and deployed flows on command" taggedAs (OnlyThisTest) in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEventsWithTimeout(30000, 2, FlowDeployableSysevents.FlowStarted)
    clearEvents()
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_STOP), None)
    expectExactlyNEvents(2, FlowDeployableSysevents.FlowStopped)
    expectExactlyNEvents(1, FlowProxyActor.BecomingPassive)
  }

  trait WithFlowTwoDeploymentsFourWorkers extends WithFlow1Created with WithWorkerNode2


  it should "deploy flow instance into all available hosts with required role" in new WithFlowTwoDeploymentsFourWorkers {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 1, 'ActiveWorkers -> 2)
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 2, 'ActiveWorkers -> 4)
  }

  trait WithFlowTwoDeploymentsFourWorkersStarted extends WithFlowTwoDeploymentsFourWorkers {
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 1, 'ActiveWorkers -> 2)
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 2, 'ActiveWorkers -> 4)
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
    expectExactlyNEvents(1, GateStubActor.RegisterSinkReceived)
    expectOneOrMoreEvents(FlowDeployableSysevents.BecomingActive)

    clearEvents()
  }


  case class KeyAndSeed(key: String, seed: String, inst: String)

  val KeyAndSeedForW1Inst1 = KeyAndSeed("k", "3", "Instance1@Worker1")
  val KeyAndSeedForW1Inst2 = KeyAndSeed("k", "4", "Instance2@Worker1")
  val KeyAndSeedForW2Inst1 = KeyAndSeed("k", "1", "Instance1@Worker2")
  val KeyAndSeedForW2Inst2 = KeyAndSeed("k", "2", "Instance2@Worker2")

  it should s"process $KeyAndSeedForW1Inst1 on Instance1@Worker1" in new WithFlowTwoDeploymentsFourWorkersStarted {
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> "1", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "xyz"), 123))
    expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"xyz"""".r)

    expectExactlyNEvents(1, FlowDeployableSysevents.ForwardedToFlow, 'InstanceId -> s"1@$worker1Address".r)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 123)
  }

  it should s"process $KeyAndSeedForW1Inst2 on Instance2@Worker1" in new WithFlowTwoDeploymentsFourWorkersStarted {
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> "1", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "xyz"), 123))
    expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"xyz"""".r)

    expectExactlyNEvents(1, FlowDeployableSysevents.ForwardedToFlow, 'InstanceId -> s"2@$worker1Address".r)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 123)
  }

  it should s"process $KeyAndSeedForW2Inst1 on Instance1@Worker2" in new WithFlowTwoDeploymentsFourWorkersStarted {
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> "1", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "xyz"), 123))
    expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"xyz"""".r)

    expectExactlyNEvents(1, FlowDeployableSysevents.ForwardedToFlow, 'InstanceId -> s"1@$worker2Address".r)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 123)
  }

  it should s"process $KeyAndSeedForW2Inst2 on Instance2@Worker2" in new WithFlowTwoDeploymentsFourWorkersStarted {
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> "1", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "xyz"), 123))
    expectOneOrMoreEvents(BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"xyz"""".r)

    expectExactlyNEvents(1, FlowDeployableSysevents.ForwardedToFlow, 'InstanceId -> s"2@$worker2Address".r)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 123)

  }

  it should s"consistently process events on correct workers" in new WithFlowTwoDeploymentsFourWorkersStarted {

    var cnt = 0
    (1 to 100) foreach { i =>
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"), cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"), cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"), cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"), cnt))
    }

    expectExactlyNEvents(100, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w2"""".r, 'InstanceId -> s"2@$worker2Address".r)
    expectExactlyNEvents(100, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w2"""".r, 'InstanceId -> s"1@$worker2Address".r)
    expectExactlyNEvents(100, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w1"""".r, 'InstanceId -> s"2@$worker1Address".r)
    expectExactlyNEvents(100, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w1"""".r, 'InstanceId -> s"1@$worker1Address".r)

    expectExactlyNEvents(400, GateStubActor.AcknowledgeAsProcessedReceived)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 1)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 50)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 100)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 150)


  }


  it should s"consistently process events on correct workers, when events sent as batches" in new WithFlowTwoDeploymentsFourWorkersStarted {

    var cnt = 0
    (1 to 100) foreach { i =>
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(
        Batch(Seq(
          EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"),
          EventFrame("eventId" -> s"$cnt-1", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"),
          EventFrame("eventId" -> s"$cnt-2", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2")
        )), 
        cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(
        Batch(Seq(
          EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"),
          EventFrame("eventId" -> s"$cnt-1", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"),
          EventFrame("eventId" -> s"$cnt-2", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1")
        )),
        cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(
        Batch(Seq(
          EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"),
          EventFrame("eventId" -> s"$cnt-1", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"),
          EventFrame("eventId" -> s"$cnt-2", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1")
        )),
        cnt))
      cnt = cnt + 1
      sendFromGateToSinks("gate1", Acknowledgeable(
        Batch(Seq(
          EventFrame("eventId" -> s"$cnt", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"),
          EventFrame("eventId" -> s"$cnt-1", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"),
          EventFrame("eventId" -> s"$cnt-2", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2")
        )),
        cnt))
    }

    expectExactlyNEvents(300, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w2"""".r, 'InstanceId -> s"2@$worker2Address".r)
    expectExactlyNEvents(300, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w2"""".r, 'InstanceId -> s"1@$worker2Address".r)
    expectExactlyNEvents(300, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w1"""".r, 'InstanceId -> s"2@$worker1Address".r)
    expectExactlyNEvents(300, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w1"""".r, 'InstanceId -> s"1@$worker1Address".r)

    expectExactlyNEvents(400, GateStubActor.AcknowledgeAsProcessedReceived)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 1)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 50)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 100)
    expectExactlyNEvents(1, GateStubActor.AcknowledgeAsProcessedReceived, 'CorrelationId -> 150)


  }

  it should "terminate in response to remove command" in new WithFlow1Created {
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_START), None)
    expectExactlyNEvents(1, FlowProxyActor.BecomingActive)
    expectSomeEventsWithTimeout(30000, 2, PassiveInputActor.PreStart)
    clearEvents()
    commandFrom1(hub1System, LocalSubj(flow1ComponentKey, T_REMOVE), None)
    expectOneOrMoreEvents(PassiveInputActor.PostStop)
  }



  it should s"reassign to available workers when one is gone, then when it restarts nothing should change" in new WithFlowTwoDeploymentsFourWorkersStarted {
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"1", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"), 1))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"2", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"), 2))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"3", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"), 3))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"4", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"), 4))

    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w2"""".r, 'InstanceId -> s"2@$worker2Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w2"""".r, 'InstanceId -> s"1@$worker2Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w1"""".r, 'InstanceId -> s"2@$worker1Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w1"""".r, 'InstanceId -> s"1@$worker1Address".r)

    clearEvents()

    restartWorkerNode2()
    expectExactlyNEvents(1, FlowDeployerActor.FlowDeploymentRemoved, 'ActiveDeployments -> 1, 'ActiveWorkers -> 2)
    expectSomeEventsWithTimeout(30000, 1, FlowDeployerActor.FlowDeploymentAdded, 'ActiveDeployments -> 2, 'ActiveWorkers -> 4)

    clearEvents()

    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"1", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"), 5))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"2", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"), 6))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"3", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"), 7))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"4", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"), 8))

    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w2"""".r, 'InstanceId -> s"@$worker1Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w2"""".r, 'InstanceId -> s"@$worker1Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w1"""".r, 'InstanceId -> s"2@$worker1Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w1"""".r, 'InstanceId -> s"1@$worker1Address".r)


    restartWorkerNode1()
    expectExactlyNEvents(1, FlowDeployerActor.FlowDeploymentRemoved, 'ActiveDeployments -> 1, 'ActiveWorkers -> 2)


    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"1", "streamKey" -> KeyAndSeedForW1Inst1.key, "streamSeed" -> KeyAndSeedForW1Inst1.seed, "abc1" -> "1@w1"), 5))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"2", "streamKey" -> KeyAndSeedForW2Inst2.key, "streamSeed" -> KeyAndSeedForW2Inst2.seed, "abc1" -> "2@w2"), 6))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"3", "streamKey" -> KeyAndSeedForW1Inst2.key, "streamSeed" -> KeyAndSeedForW1Inst2.seed, "abc1" -> "2@w1"), 7))
    sendFromGateToSinks("gate1", Acknowledgeable(EventFrame("eventId" -> s"4", "streamKey" -> KeyAndSeedForW2Inst1.key, "streamSeed" -> KeyAndSeedForW2Inst1.seed, "abc1" -> "1@w2"), 8))

    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w2"""".r, 'InstanceId -> s"@$worker2Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w2"""".r, 'InstanceId -> s"@$worker2Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"2@w1"""".r, 'InstanceId -> s"@$worker2Address".r)
    expectExactlyNEvents(1, BlackholeAutoAckSinkActor.MessageArrived, 'Contents -> """"abc":"1@w1"""".r, 'InstanceId -> s"@$worker2Address".r)

  }





}

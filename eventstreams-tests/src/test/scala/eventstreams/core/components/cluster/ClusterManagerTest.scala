package eventstreams.core.components.cluster

import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.support.{MultiNodeTestingSupport, SharedActorSystem}
import org.scalatest.FlatSpec

class ClusterManagerTest
  extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {


  val expectedPeersListInitial = "engine1"
  val expectedPeersListComplete = "engine1,engine2,worker1,worker2,worker3"

  "Cluster" should "start with 5 nodes and all peers should be discovered" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "consistently" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "and again (testing SharedActorSystem)" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should "and again ... (testing SharedActorSystem)"in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "engine2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "worker3")
  }

  it should s"recover if one node fails" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
    clearEvents()
    restartWorkerNode2()
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
  }

  it should "recover if one seed fails" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
    clearEvents()
    restartEngineNode1()
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
  }


  it should "recover if multiple node fail" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "engine1")
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
    clearEvents()
    restartWorkerNode2()
    restartWorkerNode1()
    restartWorkerNode3()
    expectSomeEventsWithTimeout(60000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
  }



}
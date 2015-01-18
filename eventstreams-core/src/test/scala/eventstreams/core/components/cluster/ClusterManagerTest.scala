package eventstreams.core.components.cluster

/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.support.{DummyNodeTestContext, SharedActorSystem}
import org.scalatest.FlatSpec

class ClusterManagerTest
  extends FlatSpec with DummyNodeTestContext with SharedActorSystem {


  val expectedPeersListInitial = "dummy1"
  val expectedPeersListComplete = "dummy1,dummy2,dummy3,dummy4,dummy5"

  "Cluster" should "start with 5 nodes and all peers should be discovered" in new WithDummyNode1 
    with WithDummyNode2 with WithDummyNode3 with WithDummyNode4 with WithDummyNode5 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy3")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy4")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy5")
  }

  it should "consistently" in new WithDummyNode1
    with WithDummyNode2 with WithDummyNode3 with WithDummyNode4 with WithDummyNode5 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy3")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy4")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy5")
  }

  it should "and again (testing SharedActorSystem)" in new WithDummyNode1
    with WithDummyNode2 with WithDummyNode3 with WithDummyNode4 with WithDummyNode5 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy3")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy4")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy5")
  }

  it should "and again ... (testing SharedActorSystem)"in new WithDummyNode1
    with WithDummyNode2 with WithDummyNode3 with WithDummyNode4 with WithDummyNode5 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy1")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy2")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy3")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy4")
    expectSomeEvents(ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy5")
  }

  it should s"recover if one node fails" in new WithDummyNode1
    with WithDummyNode2 with WithDummyNode3 with WithDummyNode4 with WithDummyNode5 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> expectedPeersListInitial, 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, 5, ClusterStateChanged, 'Peers -> expectedPeersListComplete)
    clearEvents()
    restartDummyNode4()
    expectSomeEventsWithTimeout(30000, 1, ClusterStateChanged, 'Peers -> expectedPeersListComplete, 'Node -> "dummy4")
  }



}
package eventstreams.core.components.routing

import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.support.{SharedActorSystem, MultiNodeTestingSupport}
import org.scalatest.FlatSpec

class MessageRouterTest
  extends FlatSpec with MultiNodeTestingSupport with SharedActorSystem {


  "Three nodes with message router on each, Message router" should "start" in new WithEngineNode1
    with WithEngineNode2 with WithWorkerNode1 {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "engine1,engine2,worker1", 'Node -> "engine1")
    expectSomeEvents(3, MessageRouterActor.PreStart)
  }


}
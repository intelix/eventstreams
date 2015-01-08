package eventstreams.core.components.routing

import eventstreams.engine.agents.AgentsManagerActor
import eventstreams.support.MultiNodeTestingSupport
import org.scalatest.FlatSpec

class MessageRouterTest
  extends FlatSpec with MultiNodeTestingSupport {


  "Node 1" should "start" in new WithEngineNode1 
    with WithEngineNode2 with WithWorkerNode1 with WithWorkerNode2 with WithWorkerNode3 {
    
    duringPeriodInMillis(10000) {
      expectNoEvents(AgentsManagerActor.PostRestart)
    }
  }

}
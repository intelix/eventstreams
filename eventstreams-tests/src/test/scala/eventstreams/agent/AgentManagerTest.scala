package eventstreams.agent

import eventstreams.agent.support.AgentControllerSupport
import eventstreams.engine.agents.AgentsManagerActor
import org.scalatest.FlatSpec

class AgentManagerTest
  extends FlatSpec with AgentControllerSupport  {


  "AgentManager" should "start" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.PreStart)
  }

  it should "receive a handshake from the agent" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.HandshakeReceived)
  }

  it should "create an agent proxy" in new WithEngineNode {
    expectSomeEvents(AgentsManagerActor.AgentProxyInstanceAvailable)
  }



}

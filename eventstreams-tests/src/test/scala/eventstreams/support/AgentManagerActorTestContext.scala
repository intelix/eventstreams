package eventstreams.support

import akka.actor.ActorSystem
import eventstreams.engine.agents.AgentsManagerActor

trait AgentManagerActorTestContext {

  def withAgentManager(system: ActorSystemWrapper) =
    system.start(AgentsManagerActor.props, AgentsManagerActor.id)

  
}

package eventstreams.support

import akka.actor.ActorRef
import com.typesafe.config.{Config, ConfigFactory}
import eventstreams.agent.AgentControllerActor

trait AgentControllerTestContext {

  val agentConfig: Config = ConfigFactory.load("agent-proc-test.conf")

  def withAgentController(system: ActorSystemWrapper, agentConfig: Config = agentConfig): ActorRef =
    system.start(AgentControllerActor.props(agentConfig), AgentControllerActor.id)


}

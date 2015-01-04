package eventstreams.agent.support

import akka.actor.ActorRef
import com.typesafe.config.{Config, ConfigFactory}
import core.events.support.EventAssertions
import org.scalatest.Suite

trait AgentControllerSupport extends EventAssertions with MultiActorSystemTestContext {
  _: Suite =>

  val EngineSystem = "engine"
  val AgentSystem = "agent"

  override def configs: Map[String, Config] = Map(
    AgentSystem -> ConfigFactory.load("agent-test.conf"),
    EngineSystem -> ConfigFactory.load("main-test.conf")
  )

  trait DefaultContext
    extends ConfigStorageActorTestContext
    with AgentControllerTestContext
    with AgentManagerActorTestContext {

    var agentControllerActor: Option[ActorRef] = None
    
    withSystem(AgentSystem) { sys =>
      withConfigStorage1(sys)
      agentControllerActor = Some(withAgentController(sys))
    }

    def sendToAgentController(msg: Any) = agentControllerActor.foreach(_ ! msg)
    
  }

  trait WithEngineNode extends DefaultContext {

    def startEngineNode() =
      withSystem(EngineSystem) { sys =>
        withConfigStorage2(sys)
        withAgentManager(sys)
      }

    def restartEngineNode() = {
      destroySystem(EngineSystem)
      startEngineNode()
    }

    startEngineNode()
    
  }


}

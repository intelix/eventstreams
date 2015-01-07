package eventstreams.agent.support

import akka.actor.ActorRef
import com.typesafe.config.{Config, ConfigFactory}
import core.events.support.EventAssertions
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.messages.{Command, ComponentKey, LocalSubj, TopicKey}
import eventstreams.support.GateStubTestContext
import org.scalatest.Suite
import play.api.libs.json.JsValue

trait DatasourceTestingSupport extends EventAssertions with MultiActorSystemTestContext {
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

    def startAgentNode() =
    withSystem(AgentSystem) { sys =>
      withConfigStorage1(sys)
      agentControllerActor = Some(withAgentController(sys))
    }

    def sendToAgentController(msg: Any) = agentControllerActor.foreach(_ ! msg)

    def restartAgentNode() = {
      destroySystem(AgentSystem)
      agentControllerActor = None
      startAgentNode()
    }

    startAgentNode()

  }

  trait WithMessageRouter extends MessageRouterActorTestContext with DefaultTopicKeys {

    var messageRouterActor: Option[ActorRef] = None

    def startMessageRouter(sys: ActorSystemWrapper) =
      messageRouterActor = Some(withMessageRouter(sys))

    def stopMessageRouter() =
      messageRouterActor = None

    def sendCommand(subject: Any, data: Option[JsValue]) =
      messageRouterActor.foreach(_ ! Command(ActorRef.noSender, subject, None, data))

    def sendCommand(localRoute: String, topic: TopicKey, data: Option[JsValue]) =
      messageRouterActor.foreach(_ ! Command(ActorRef.noSender, LocalSubj(ComponentKey(localRoute), topic), None, data))

  }

  trait WithEngineNode extends DefaultContext with WithMessageRouter with GateStubTestContext with SubscriberStubActor1 {

    def startEngineNode() =
      withSystem(EngineSystem) { sys =>

        startMessageRouter(sys)

        withConfigStorage2(sys)
        withAgentManager(sys)
      }

    def startGate(name: String) = withSystem(EngineSystem) { sys =>
      withGateStub(sys, name)
    }

    def restartEngineNode() = {
      stopMessageRouter()
      destroySystem(EngineSystem)
      startEngineNode()
    }

    startEngineNode()

  }


}

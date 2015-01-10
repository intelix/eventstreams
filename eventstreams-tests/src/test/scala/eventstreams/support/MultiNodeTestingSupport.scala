package eventstreams.support

import akka.actor.ActorRef
import com.typesafe.config.{Config, ConfigFactory}
import core.events.support.EventAssertions
import org.scalatest.Suite

trait MultiNodeTestingSupport extends EventAssertions with MultiActorSystemTestContext {
  _: Suite with ActorSystemManagement =>

  val AgentSystemPrefix = "agent"
  val EngineSystemPrefix = "engine"
  val WorkerSystemPrefix = "worker"
  val WebSystemPrefix = "web"
  val WebPlaySystemPrefix = "webplay"
  val PluginSystemPrefix = "plugin"

  override def configs: Map[String, Config] = Map(
    AgentSystemPrefix + "1" -> ConfigFactory.load("agent-proc-test.conf"),
    AgentSystemPrefix + "2" -> ConfigFactory.load("agent2-proc-test.conf"),
    EngineSystemPrefix + "1" -> ConfigFactory.load("main-proc-test.conf"),
    EngineSystemPrefix + "2" -> ConfigFactory.load("main2-proc-test.conf"),
    WorkerSystemPrefix + "1" -> ConfigFactory.load("worker-proc-test.conf"),
    WorkerSystemPrefix + "2" -> ConfigFactory.load("worker2-proc-test.conf"),
    WorkerSystemPrefix + "3" -> ConfigFactory.load("worker3-proc-test.conf"),
    WebSystemPrefix + "1" -> ConfigFactory.load("web-proc-test.conf"),
    WebSystemPrefix + "2" -> ConfigFactory.load("web2-proc-test.conf"),
    WebPlaySystemPrefix + "1" -> ConfigFactory.load("webplay-proc-test.conf"),
    WebPlaySystemPrefix + "2" -> ConfigFactory.load("webplay2-proc-test.conf"),
    PluginSystemPrefix + "1" -> ConfigFactory.load("plugin-proc-test.conf")
  )

  def withSystem[T](prefix: String, idx: Int)(f: ActorSystemWrapper => T): T = withSystem[T](prefix + idx)(f)

  trait WithAgentNode
    extends ConfigStorageActorTestContext
    with AgentControllerTestContext
    with AgentManagerActorTestContext {

    var agentControllerActor: Map[Int, ActorRef] = Map()

    def startAgentNode(systemIndex: Int) =
      withSystem(AgentSystemPrefix, systemIndex) { sys =>
        withConfigStorage(systemIndex, sys)
        agentControllerActor = agentControllerActor + (systemIndex -> withAgentController(sys))
      }

    def sendToAgentController(systemIndex: Int, msg: Any) = agentControllerActor.get(systemIndex).foreach(_ ! msg)

    def restartAgentNode(systemIndex: Int) = {
      destroySystem(AgentSystemPrefix + systemIndex.toString)
      agentControllerActor = agentControllerActor - systemIndex
      startAgentNode(systemIndex)
    }

  }

  trait WithAgentNode1 extends WithAgentNode {
    def startAgentNode1(): Unit = startAgentNode(1)

    def sendToAgentController1(msg: Any) = sendToAgentController(1, msg)

    def restartAgentNode1(): Unit = restartAgentNode(1)

    startAgentNode1()
  }

  trait WithAgentNode2 extends WithAgentNode {
    def startAgentNode2(): Unit = startAgentNode(2)

    def sendToAgentController2(msg: Any) = sendToAgentController(2, msg)

    def restartAgentNode2(): Unit = restartAgentNode(2)

    startAgentNode2()
  }


  trait WithEngineNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with GateStubTestContext
    with SubscribingComponentStub
    with AgentManagerActorTestContext
    with ClusterTestContext
    with ClusterManagerActorTestContext {

    def startEngineNode(systemIndex: Int) =
      withSystem(EngineSystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)
        }


        withConfigStorage(20 + systemIndex, sys)
        withAgentManager(sys)

      }

    def startGate(systemIndex: Int, name: String) = withSystem(EngineSystemPrefix, systemIndex) { sys =>
      withGateStub(sys, name)
    }

    def restartEngineNode(systemIndex: Int) = {
      destroySystem(EngineSystemPrefix + systemIndex.toString)
      startEngineNode(systemIndex)
    }

  }

  trait WithEngineNode1 extends WithEngineNode {
    def engine1System = getSystem(EngineSystemPrefix + "1")
    
    def startEngineNode1(): Unit = startEngineNode(1)

    def startGate1(name: String): Unit = startGate(1, name)

    def restartEngineNode1(): Unit = restartEngineNode(1)

    startEngineNode1()
  }

  trait WithEngineNode2 extends WithEngineNode {
    def engine2System = getSystem(EngineSystemPrefix + "2")

    def startEngineNode2(): Unit = startEngineNode(2)

    def startGate2(name: String): Unit = startGate(2, name)

    def restartEngineNode2(): Unit = restartEngineNode(2)

    startEngineNode2()
  }


  trait WithWorkerNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with ClusterTestContext
    with ClusterManagerActorTestContext {

    def startWorkerNode(systemIndex: Int) =
      withSystem(WorkerSystemPrefix, systemIndex) { implicit sys =>
        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)
        }
        withConfigStorage(30 + systemIndex, sys)
      }

    def restartWorkerNode(systemIndex: Int) = {
      destroySystem(WorkerSystemPrefix + systemIndex.toString)
      startWorkerNode(systemIndex)
    }

  }

  trait WithWorkerNode1 extends WithWorkerNode {
    def worker1System = getSystem(WorkerSystemPrefix + "1")
    def startWorkerNode1(): Unit = startWorkerNode(1)
    def restartWorkerNode1(): Unit = restartWorkerNode(1)
    startWorkerNode1()
  }
  trait WithWorkerNode2 extends WithWorkerNode {
    def worker2System = getSystem(WorkerSystemPrefix + "2")
    def startWorkerNode2(): Unit = startWorkerNode(2)
    def restartWorkerNode2(): Unit = restartWorkerNode(2)
    startWorkerNode2()
  }
  trait WithWorkerNode3 extends WithWorkerNode {
    def worker3System = getSystem(WorkerSystemPrefix + "3")
    def startWorkerNode3(): Unit = startWorkerNode(3)
    def restartWorkerNode3(): Unit = restartWorkerNode(3)
    startWorkerNode3()
  }


}

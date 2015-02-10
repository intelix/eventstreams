package eventstreams.support

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

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Suite


trait WorkerNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val WorkerSystemPrefix = "worker"

  override def configs: Map[String, Config] = super.configs ++ Map(
    WorkerSystemPrefix + "1" -> ConfigFactory.load("worker1-proc-test.conf"),
    WorkerSystemPrefix + "2" -> ConfigFactory.load("worker2-proc-test.conf"),
    WorkerSystemPrefix + "3" -> ConfigFactory.load("worker3-proc-test.conf")
  )


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
    def worker1Address = "akka.tcp://engine@localhost:12531"

    def worker1System = getSystem(WorkerSystemPrefix + "1")

    def startWorkerNode1(): Unit = startWorkerNode(1)

    def restartWorkerNode1(): Unit = restartWorkerNode(1)

    startWorkerNode1()
  }

  trait WithWorkerNode2 extends WithWorkerNode {
    def worker2Address = "akka.tcp://engine@localhost:12532"

    def worker2System = getSystem(WorkerSystemPrefix + "2")

    def startWorkerNode2(): Unit = startWorkerNode(2)

    def restartWorkerNode2(): Unit = restartWorkerNode(2)

    startWorkerNode2()
  }

  trait WithWorkerNode3 extends WithWorkerNode {
    def worker3Address = "akka.tcp://engine@localhost:12533"

    def worker3System = getSystem(WorkerSystemPrefix + "3")

    def startWorkerNode3(): Unit = startWorkerNode(3)

    def restartWorkerNode3(): Unit = restartWorkerNode(3)

    startWorkerNode3()
  }


}

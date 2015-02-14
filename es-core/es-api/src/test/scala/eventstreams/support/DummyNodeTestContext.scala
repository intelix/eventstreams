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


trait DummyNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val DummySystemPrefix = "hub"

  override def configs: Map[String, Config] = super.configs ++ Map(
    DummySystemPrefix + "1" -> ConfigFactory.load("dummy1-proc-test.conf"),
    DummySystemPrefix + "2" -> ConfigFactory.load("dummy2-proc-test.conf"),
    DummySystemPrefix + "3" -> ConfigFactory.load("dummy3-proc-test.conf"),
    DummySystemPrefix + "4" -> ConfigFactory.load("dummy4-proc-test.conf"),
    DummySystemPrefix + "5" -> ConfigFactory.load("dummy5-proc-test.conf")
  )


  trait WithDummyNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with SubscribingComponentStub
    with ClusterTestContext
    with ClusterManagerActorTestContext {

    def startDummyNode(systemIndex: Int) =
      withSystem(DummySystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)
        }

        withConfigStorage(900 + systemIndex, sys)

      }


    def restartDummyNode(systemIndex: Int) = {
      destroySystem(DummySystemPrefix + systemIndex.toString)
      startDummyNode(systemIndex)
    }

  }

  trait WithDummyNode1 extends WithDummyNode {
    def dummy1Address = "akka.tcp://hub@localhost:12521"

    def dummy1System = getSystem(DummySystemPrefix + "1")

    def startDummyNode1(): Unit = startDummyNode(1)

    def restartDummyNode1(): Unit = restartDummyNode(1)

    startDummyNode1()
  }

  trait WithDummyNode2 extends WithDummyNode {
    def dummy2Address = "akka.tcp://hub@localhost:12522"

    def dummy2System = getSystem(DummySystemPrefix + "2")

    def startDummyNode2(): Unit = startDummyNode(2)

    def restartDummyNode2(): Unit = restartDummyNode(2)

    startDummyNode2()
  }

  trait WithDummyNode3 extends WithDummyNode {
    def dummy3Address = "akka.tcp://hub@localhost:12523"

    def dummy3System = getSystem(DummySystemPrefix + "3")

    def startDummyNode3(): Unit = startDummyNode(3)

    def restartDummyNode3(): Unit = restartDummyNode(3)

    startDummyNode3()
  }

  trait WithDummyNode4 extends WithDummyNode {
    def dummy4Address = "akka.tcp://hub@localhost:12524"

    def dummy4System = getSystem(DummySystemPrefix + "4")

    def startDummyNode4(): Unit = startDummyNode(4)

    def restartDummyNode4(): Unit = restartDummyNode(4)

    startDummyNode4()
  }

  trait WithDummyNode5 extends WithDummyNode {
    def dummy5Address = "akka.tcp://hub@localhost:12525"

    def dummy5System = getSystem(DummySystemPrefix + "5")

    def startDummyNode5(): Unit = startDummyNode(5)

    def restartDummyNode5(): Unit = restartDummyNode(5)

    startDummyNode5()
  }



}

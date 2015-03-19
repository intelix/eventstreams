package eventstreams

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

import akka.actor.Props
import com.typesafe.config.{Config, ConfigFactory}
import eventstreams.gauges.{GaugesManagerActor, GaugesManagerConstants}
import eventstreams.support._
import org.scalatest.Suite

trait GaugesServiceNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val GaugesSystemPrefix = "hub"

  override def configs: Map[String, Config] = super.configs ++ Map(
    GaugesSystemPrefix + "1" -> ConfigFactory.load("gauges1-proc-test.conf")
  )


  trait WithGaugesNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with SubscribingComponentStub
    with ClusterTestContext
    with ClusterManagerActorTestContext {

    var counter = 0

    def startGaugesNode(systemIndex: Int) =
      withSystem(GaugesSystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)

          sys.start(Props(new GaugesManagerActor(sys.config, cluster)), GaugesManagerConstants.id)
        }

      }

    def sendToGaugeService(systemIndex: Int, e: Any) = withSystem(GaugesSystemPrefix, systemIndex) { implicit sys => sys.rootUserActorSelection(GaugesManagerConstants.id) ! e }


    def sendEventFrameToGaugeService(systemIndex: Int, e: EventFrame) = {
      counter = counter + 1
      sendToGaugeService(systemIndex, Acknowledgeable(e, counter))
    }


    def restartGaugesNode(systemIndex: Int) = {
      destroySystem(GaugesSystemPrefix + systemIndex.toString)
      startGaugesNode(systemIndex)
    }

  }

  trait WithGaugesNode1 extends WithGaugesNode {
    def gauges1Address = "akka.tcp://hub@localhost:12521"

    def gauges1System = getSystem(GaugesSystemPrefix + "1")

    def startGaugesNode1(): Unit = startGaugesNode(1)

    def restartGaugesNode1(): Unit = restartGaugesNode(1)

    def sendEventFrameToGaugeService1(e: EventFrame) = sendEventFrameToGaugeService(1, e)
    def sendToGaugeService1(e: Any) = sendToGaugeService(1, e)

    startGaugesNode1()
  }

}

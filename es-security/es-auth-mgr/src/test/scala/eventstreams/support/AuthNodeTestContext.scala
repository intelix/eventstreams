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


trait AuthNodeTestContext extends MultiNodeTestingSupport {
  _: Suite with ActorSystemManagement =>

  val AuthSystemPrefix = "auth"

  override def configs: Map[String, Config] = super.configs ++ Map(
    AuthSystemPrefix + "1" -> ConfigFactory.load("auth1-proc-test.conf")
  )


  trait WithAuthNode
    extends ConfigStorageActorTestContext
    with MessageRouterActorTestContext
    with SubscribingComponentStub
    with ClusterTestContext
    with ClusterManagerActorTestContext {

    def startAuthNode(systemIndex: Int) =
      withSystem(AuthSystemPrefix, systemIndex) { implicit sys =>

        withCluster(sys) { cluster =>
          startMessageRouter(sys, cluster)
          startClusterManager(sys, cluster)

        }

        withConfigStorage(900 + systemIndex, sys)

        sys.start(BasicAuthActorStub.props("auth"), "auth");

      }



  }

  trait WithAuthNode1 extends WithAuthNode {
    def auth1Address = "akka.tcp://engine@localhost:12621"

    def auth1System = getSystem(AuthSystemPrefix + "1")

    def startAuthNode1(): Unit = startAuthNode(1)

    startAuthNode1()
  }


}

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
package eventstreams.support

import akka.actor.ActorRef
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.gates.GateManagerActor

trait GateManagerActorTestContext extends DefaultTopicKeys with ClusterTestContext {

  def startGateManager(system: ActorSystemWrapper) =
    withCluster(system) { cluster =>
      system.start(GateManagerActor.props(system.config, cluster), GateManagerActor.id)
    }

  def gatewManagerActorSelection(system: ActorSystemWrapper) = system.rootUserActorSelection(GateManagerActor.id)

  def startGatePublisherStub1(system: ActorSystemWrapper, address: String = "/user/gate1") =
    system.start(GatePublisherStubActor.props(address), GatePublisherStubActor.id + "1")
  def startGatePublisherStub2(system: ActorSystemWrapper, address: String = "/user/gate1") =
    system.start(GatePublisherStubActor.props(address), GatePublisherStubActor.id + "2")

  def startGateSinkStub1(system: ActorSystemWrapper, address: String = "/user/gate1") =
    system.start(GateSinkStubActor.props(address, GateSinkStubActor.id + "1"), GateSinkStubActor.id + "1")
  def startGateSinkStub2(system: ActorSystemWrapper, address: String = "/user/gate1") =
    system.start(GateSinkStubActor.props(address, GateSinkStubActor.id + "2"), GateSinkStubActor.id + "2")

}

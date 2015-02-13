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

import akka.cluster.Cluster
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.{Command, ComponentKey, LocalSubj, TopicKey}
import play.api.libs.json.{JsValue, Json}

trait MessageRouterActorTestContext extends DefaultTopicKeys {

  def startMessageRouter(system: ActorSystemWrapper, cluster: Cluster) =
    system.start(MessageRouterActor.props(cluster, system.config), MessageRouterActor.id)

  def sendCommand(system: ActorSystemWrapper, subject: Any, data: Option[JsValue]) =
    messageRouterActorSelection(system) ! Command(subject, None, data.map(Json.stringify))

  def sendCommand(system: ActorSystemWrapper, localRoute: String, topic: TopicKey, data: Option[JsValue]) =
    messageRouterActorSelection(system) ! Command(LocalSubj(ComponentKey(localRoute), topic), None, data.map(Json.stringify))

  def messageRouterActorSelection(system: ActorSystemWrapper) = system.rootUserActorSelection(MessageRouterActor.id)

}

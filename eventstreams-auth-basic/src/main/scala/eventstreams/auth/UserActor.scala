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
package eventstreams.auth

import akka.actor._
import eventstreams.core.Tools.configHelper
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.agent.core._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._
import scalaz.\/

object UserActor {
  def props(id: String) = Props(new UserActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class UserActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with RouteeActor
  with ActorWithTicks
  with WithMetrics {

  var name: Option[String] = None
  var roles: Set[String] = Set()

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name
    roles = (props ##> 'roles | Array[JsValue]()).map(_.as[String]).toSet
  }

  override def onInitialConfigApplied(): Unit = context.parent ! UserAvailable(key, name.get, roles, self)


  override def key = ComponentKey(id)
}

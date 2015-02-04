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
import eventstreams.core.WithMetrics
import eventstreams.core.actors.{ActorTools, ActorWithConfigStore, ActorWithTicks, RouteeActor, _}
import eventstreams.core.messages.ComponentKey
import play.api.libs.json._

import scalaz.Scalaz._

object UserRoleActor {
  def props(id: String) = Props(new UserRoleActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class UserRoleActor(id: String)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with ActorWithTicks
  with WithMetrics {

  var name: Option[String] = None
  var permissions: Option[RolePermissions] = None

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name

    var map = (props ##> 'domains | Array[JsValue]()).map { d =>
      for (
        moduleId <- d ~> 'moduleId;
        func <- (d ##> 'functions).map(_.map { f =>
          for (
            id <- f ~> 'id;
            pattern <- f ~> 'p;
            allow <- f ?> 'a
          ) yield FunctionPermission(id, pattern, allow)
        }.collect { case Some(x) => x}.toSet)
      ) yield SecuredDomain(moduleId) -> func
    }.collect { case Some(x) => x}.toMap

    permissions = Some(RolePermissions(map))

  }

  override def onInitialConfigApplied(): Unit = context.parent ! UserRoleAvailable(key, name.get, permissions.get, self)

  override def key = ComponentKey(id)
}

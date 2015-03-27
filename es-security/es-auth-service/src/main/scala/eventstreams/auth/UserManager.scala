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

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.io.Source
import scalaz.Scalaz._

trait UserManagerSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Auth.UserManager"
}

object UserManager extends ActorObjWithConfig {
  def id = "users"

  def props(implicit config: Config) = Props(new UserManagerActor(config))
}

case class UserAvailable(id: ComponentKey, name: String, hash: Option[String], roles: Set[String], ref: ActorRef) extends Model


class UserManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with RouteeModelManager[UserAvailable]
  with NowProvider
  with UserManagerSysevents
  with WithSyseventPublisher {

  override val configSchema = Some(Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.auth.users.main-schema"))).mkString))

  override val entityId = UserManager.id


  override def publishList(): Unit = {
    super.publishList()
    context.parent ! AvailableUsers(entries)
  }

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def handler: Receive = {
    case x : UserAvailable => addEntry(x)
  }

  override def list = Some(Json.toJson(entries.map { x =>
    Json.obj(
      "ckey" -> x.id.key,
      "name" -> x.name,
      "roles" -> Json.toJson(x.roles.toSeq)
    )
  }.toSeq))

  override def startModelActor(key: String, config: ModelConfigSnapshot): ActorRef = UserActor.start(key, config)
}

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
import eventstreams.core.actors.{ActorObjWithConfig, ActorWithComposableBehavior, ActorWithConfigStore, InitialConfig, RouteeActor}
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

case class UserAvailable(id: ComponentKey, name: String, hash: Option[String], roles: Set[String], ref: ActorRef)


class UserManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider
  with UserManagerSysevents
  with WithSyseventPublisher {

  val configSchema = Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.auth.users.main-schema"))).mkString)

  override val key = ComponentKey(UserManager.id)
  var entries: List[UserAvailable] = List()

  override def partialStorageKey: Option[String] = Some("user/")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def listUpdate() = {
    T_LIST !! list
    context.parent ! AvailableUsers(entries)

  }

  def list = Some(Json.toJson(entries.map { x =>
    Json.obj(
      "ckey" -> x.id.key,
      "name" -> x.name,
      "roles" -> Json.toJson(x.roles.toSeq)
    )
  }.toSeq))


  def publishConfigTpl(): Unit = T_CONFIGTPL !! Some(configSchema)

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => listUpdate()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => addUser(None, maybeData, None)
  }

  override def onTerminated(ref: ActorRef): Unit = {
    entries = entries.filter(_.ref != ref)
    listUpdate()
    super.onTerminated(ref)
  }

  def handler: Receive = {
    case x: UserAvailable =>
      entries = (entries.filter(_.id != x.id) :+ x).sortBy(_.name)
      listUpdate()
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = addUser(Some(key), Some(props), maybeState)

  private def addUser(k: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      val entryKey = k | (key / shortUUID).key
      var json = data
      if (k.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = UserActor.start(entryKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("User successfully created")
    }


}

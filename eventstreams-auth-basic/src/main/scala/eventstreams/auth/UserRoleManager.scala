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
import com.typesafe.config.Config
import eventstreams.core.actors.{ActorObjWithConfig, ActorWithComposableBehavior, ActorWithConfigStore, InitialConfig, RouteeActor}
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{Fail, NowProvider, OK}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json._
import play.api.libs.json.extensions._
import collection.JavaConversions._

import scala.io.Source
import scalaz.Scalaz._


object UserRoleManager extends ActorObjWithConfig {
  def id = "userroles"

  def props(implicit config: Config) = Props(new UserRoleManagerActor(config))
}

case class SecuredDomainInfo(id: String, name: String)

case class FunctionPermissionInfo(id: String, topic: String, name: String)

case class SecuredDomainPermissions(domain: SecuredDomainInfo, permissions: Set[FunctionPermissionInfo])

case class UserRoleAvailable(id: ComponentKey, name: String, permissions: RolePermissions, ref: ActorRef)

class UserRoleManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider {

  val securityDomains = sysconfig.as[Option[List[Config]]]("eventstreams.security.domains")

  val permissions = securityDomains.map { list =>
    list.map { conf =>
      for (
        name <- conf.as[Option[String]]("name");
        moduleId <- conf.as[Option[String]]("module-id");
        set <- conf.as[Option[Config]]("functions")
          .map {
          functionsBranch =>
            val listOfKeys = functionsBranch.entrySet().toList.map(_.getKey.split('.').head)
            listOfKeys.map { key =>
              val funcConf = functionsBranch.getConfig(key)
              for (
                fTopic <- funcConf.as[Option[String]]("topic");
                fName <- funcConf.as[Option[String]]("name")
              ) yield FunctionPermissionInfo(key, fTopic, fName)
            }.collect { case Some(x) => x}.toSet
        }
      ) yield SecuredDomainPermissions(SecuredDomainInfo(moduleId, name), set)
    }.collect { case Some(x) => x}
  } | List()
  val configSchema = {
    val template = Json.parse(
      Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("eventstreams.auth.user-roles.main-schema"))).mkString)
    permissions.foldLeft[JsValue](template) {
      case (result, next) =>
        result.set(__ \ "properties" \ (next.domain.id + "_mode") -> Json.obj(
          "propertyOrder" -> 900,
          "title" -> next.domain.name,
          "type" -> "string",
          "enum" -> Json.arr("Allow selected", "Deny selected")
        )).set(__ \ "properties" \ next.domain.id -> Json.obj(
          "propertyOrder" -> 900,
          "title" -> "Actions",
          "type" -> "array",
          "uniqueItems" -> true,
          "format" -> "checkbox",
          "items" -> Json.obj(
            "type" -> "string",
            "enum" -> Json.toJson(next.permissions.map(_.name).toSeq)
          )
        ))
    }
  }

  
  override val key = ComponentKey(UserRoleManager.id)
  var entries: List[UserAvailable] = List()

  override def partialStorageKey: Option[String] = Some("userrole/")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def listUpdate() = T_LIST !! list

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
      entries = (entries :+ x).sortBy(_.name)
      listUpdate()
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = addUser(Some(key), Some(props), maybeState)

  private def addUser(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      val entryKey = key | "user/" + shortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = UserActor.start(entryKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("User role successfully created")
    }


}

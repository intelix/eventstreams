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
import net.ceedubs.ficus.Ficus._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.JavaConversions._
import scala.io.Source
import scalaz.Scalaz._

trait UserRoleManagerSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Auth.UserRoleManager"
}

object UserRoleManager extends ActorObjWithConfig {
  def id = "userroles"

  def props(implicit config: Config) = Props(new UserRoleManagerActor(config))
}

case class SecuredDomainInfo(id: String, name: String)

case class FunctionPermissionInfo(id: String, topic: String, name: String)

case class SecuredDomainPermissions(domain: SecuredDomainInfo, permissions: Set[FunctionPermissionInfo])

case class UserRoleAvailable(id: ComponentKey, name: String, permissions: RolePermissions, ref: ActorRef) extends Model

class UserRoleManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeModelManager[UserRoleAvailable]
  with NowProvider
  with UserRoleManagerSysevents
  with WithSyseventPublisher {

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
    }.collect { case Some(x) => x}.sortBy(_.domain.name)
  } | List()
  override val configSchema = Some({
    val template = Json.parse(
      Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("eventstreams.auth.user-roles.main-schema"))).mkString)
    permissions.foldLeft[(JsValue, Int)]((template, 0)) {
      case ((result, c), next) =>
        (result.set(__ \ "properties" \ next.domain.id -> Json.obj(
          "propertyOrder" -> (500 + c ),
          "title" -> next.domain.name,
          "type" -> "array",
          "uniqueItems" -> true,
          "format" -> "checkbox",
          "items" -> Json.obj(
            "type" -> "string",
            "enum" -> Json.toJson(next.permissions.map(_.name).toSeq)
          )
        )), c + 1)
    }._1
  })

  
  override val key = ComponentKey(UserRoleManager.id)

  override def publishList(): Unit = {
    super.publishList()
    context.parent ! AvailableUserRoles(entries)
  }

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior
  
  def handler: Receive = {
    case x : UserRoleAvailable => addEntry(x)
  }



  override def list = Some(Json.toJson(entries.map { x =>
    Json.obj(
      "ckey" -> x.id.key,
      "name" -> x.name
    )
  }.toSeq))

  override def startModelActor(key: String): ActorRef = UserRoleActor.start(key, permissions)
}

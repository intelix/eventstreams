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

import java.security.MessageDigest

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents._
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz.\/

trait UserSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {

  val PasswordChanged = 'PasswordChanged.info

  override def componentId: String = "Auth.User"
}

object UserActor {
  def props(id: String) = Props(new UserActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class UserActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with RouteeActor
  with RouteeModelInstance
  with ActorWithTicks
  with WithMetrics
  with UserSysevents
  with WithSyseventPublisher {

  private val sha = MessageDigest.getInstance("SHA-256")
  override def storageKey: Option[String] = Some(id)

  var name: Option[String] = None
  var passwordHash: Option[String] = None
  var roles: Set[String] = Set()

  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> key.key, 'name -> name)

  def sha256(s: String): String = {
    sha.digest(s.getBytes)
      .foldLeft("")((s: String, b: Byte) => s +
      Character.forDigit((b & 0xf0) >> 4, 16) +
      Character.forDigit(b & 0x0f, 16))
  }

  override def applyConfig(key: String, props: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name
    val password = props ~> 'password
    passwordHash = props ~> 'passwordHash
    roles = (props ~> 'roles | "").split(",").map(_.trim).filterNot(_.isEmpty).toSet

    password.foreach { p =>
      passwordHash = Some(sha256(p))
      val newProps = props.delete(__ \ "password").set(__ \ "passwordHash" -> JsString(passwordHash.get))
      updateWithoutApplyConfigProps(newProps)
      PasswordChanged >> ('Hash -> passwordHash.get)
    }
  }


  override def info = Some(Json.obj(
    "name" -> (name | "n/a"),
    "roles" -> (roles.mkString(", ") match {
      case "" => "None"
      case x => x
    })
  ))


  override def publishAvailable() = context.parent ! UserAvailable(key, name | "n/a", passwordHash, roles, self)


  override def key = ComponentKey(id)


}

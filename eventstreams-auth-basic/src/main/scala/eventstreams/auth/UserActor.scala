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

import akka.actor._
import core.events.EventOps.symbolToEventOps
import core.events._
import core.events.ref.ComponentWithBaseEvents
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

trait UserEvents extends ComponentWithBaseEvents with BaseActorEvents {

  val PasswordChanged = 'PasswordChanged.info

  override def componentId: String = "Actor.User"
}

object UserActor {
  def props(id: String) = Props(new UserActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class UserActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with RouteeActor
  with ActorWithTicks
  with WithMetrics
  with UserEvents
  with WithEventPublisher {

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

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
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

  private def publishInfo() = T_INFO !! info
  private def publishProps() = T_PROPS !! propsConfig

  override def afterApplyConfig(): Unit = {
    publishInfo()
    publishProps()
  }

  def info = Some(Json.obj(
    "name" -> (name | "n/a"),
    "roles" -> (roles.mkString(", ") match {
      case "" => "None"
      case x => x
    })
  ))


  override def onInitialConfigApplied(): Unit = context.parent ! UserAvailable(key, name | "n/a", roles, self)


  override def key = ComponentKey(id)


  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = topic match {
    case T_KILL =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }



}

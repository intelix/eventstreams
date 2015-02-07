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
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.{OK, Fail, WithMetrics}
import eventstreams.core.actors.{ActorTools, ActorWithConfigStore, ActorWithTicks, RouteeActor, _}
import eventstreams.core.messages.{TopicKey, ComponentKey}
import eventstreams.model.{DomainPermissions, FunctionPermission, SecuredDomain, RolePermissions}
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz.\/

trait UserRoleEvents extends ComponentWithBaseEvents with BaseActorEvents {

  val RolePermissionsChanged = 'RolePermissionsChanged.info

  override def componentId: String = "Actor.UserRole"
}



object UserRoleActor {
  def props(id: String, availableDomains: List[SecuredDomainPermissions]) = Props(new UserRoleActor(id, availableDomains))

  def start(id: String, availableDomains: List[SecuredDomainPermissions])(implicit f: ActorRefFactory) =
    f.actorOf(props(id, availableDomains), ActorTools.actorFriendlyId(id))
}


class UserRoleActor(id: String, availableDomains: List[SecuredDomainPermissions])
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with ActorWithTicks
  with WithMetrics
  with UserRoleEvents
  with WithEventPublisher {

  override def storageKey: Option[String] = Some(id)


  var name: Option[String] = None
  var permissions: Option[RolePermissions] = None

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name
    val seq = availableDomains.flatMap { sdp =>
      val domainId = sdp.domain.id
      val setOfFunctions = (props ##> domainId).map { arr =>
        arr.map { name =>
          sdp.permissions.find { next => name.asOpt[String].contains(next.name) }.map { v => FunctionPermission(v.topic) }
        }.collect { case Some(x) => x }
      }
      setOfFunctions.map { sof =>
        List(DomainPermissions(SecuredDomain(domainId), sof))
      } | List()

    }

    permissions = Some(RolePermissions(seq))

  }

  private def publishInfo() = T_INFO !! info
  private def publishProps() = T_PROPS !! propsConfig

  override def afterApplyConfig(): Unit = {
    publishInfo()
    publishProps()
  }



  def permissionByTopic(topic: String) = availableDomains.collectFirst {
    case x if x.permissions.exists(_.topic == topic) => x.permissions.find(_.topic == topic).get
  }
  
  def info = Some(Json.obj(
    "name" -> (name | "n/a"),
    "permissions" -> permissions.map { p =>
      val set = p.domainPermissions.flatMap { l =>
        l.permissions.map{ perm => permissionByTopic(perm.topicPattern) }.toList.collect { case Some(x) => x.name}
      }
      set.mkString(", ") match {
        case "" => "None allowed"
        case x => x
      }
    }
  ))


  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = topic match {
    case T_KILL =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateAndApplyConfigProps(data)
      ) yield {
        publishAvailableUserRole()
        result
      }
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }

  def publishAvailableUserRole() =
    context.parent ! UserRoleAvailable(key, name | "n/a", permissions | RolePermissions(Seq()), self)
  override def onInitialConfigApplied(): Unit = publishAvailableUserRole()

  

  override def key = ComponentKey(id)
}

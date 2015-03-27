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

package eventstreams.desktopnotifications

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.alerts.AlertLevel
import eventstreams.core.actors._
import play.api.libs.json.Json

import scala.util.matching.Regex
import scalaz.Scalaz._

trait DesktopNotificationsSubscriptionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "DesktopNotifications.Subscription"
}

object DesktopNotificationsSubscriptionActor {
  def props(id: String, config: ModelConfigSnapshot) = Props(new DesktopNotificationsSubscriptionActor(id, config))

  def start(id: String, config: ModelConfigSnapshot)(implicit f: ActorRefFactory) = f.actorOf(props(id, config), ActorTools.actorFriendlyId(id))
}


sealed trait DesktopNotificationsSubscriptionState {
  def details: Option[String]
}

case class DesktopNotificationsSubscriptionStateUnknown(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStateActive(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStatePassive(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStateError(details: Option[String] = None) extends DesktopNotificationsSubscriptionState


class DesktopNotificationsSubscriptionActor(val entityId: String, val initialConfig: ModelConfigSnapshot)
  extends ActorWithActivePassiveBehaviors
  with RouteeModelInstance
  with RouteeWithStartStopHandler
  with NowProvider
  with DesktopNotificationsSubscriptionSysevents
  with WithSyseventPublisher {

  val T_SIGNAL = TopicKey("desktopnotification.signal")

  var currentState: DesktopNotificationsSubscriptionState = DesktopNotificationsSubscriptionStateUnknown(Some("Initialising"))

  val name = propsConfig ~> 'name | "default"
  val created = prettyTimeFormat(metaConfig ++> 'created | now)

  val signalClass = propsConfig ~> 'signalClass | "default"
  val signalClassR = Some(signalClass.r)
  val signalSubclass = propsConfig ~> 'signalSubclass
  val signalSubclassR = signalSubclass.map(_.r)
  val level = AlertLevel.fromString(propsConfig ~> 'level | "Very low")
  val conflate = propsConfig ?> 'conflate | true
  val autoCloseSec = propsConfig +> 'autoClose | 10


  override def preStart(): Unit = {
    super.preStart()

    if (metaConfig ?> 'lastStateActive | false)
      goActive()
    else
      goPassive()

    publishProps()
    publishInfo()

  }

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case DesktopNotificationsSubscriptionStateUnknown(_) => "unknown"
    case DesktopNotificationsSubscriptionStateActive(_) => "active"
    case DesktopNotificationsSubscriptionStatePassive(_) => "passive"
    case DesktopNotificationsSubscriptionStateError(_) => "error"
  }

  override def info = Some(Json.obj(
    "name" -> name,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString,
    "class" -> signalClass,
    "subclass" -> (signalSubclass | "-"),
    "level" -> level.name,
    "conflate" -> conflate,
    "autoClose" -> autoCloseSec
  ))


  override def onBecameActive(): Unit = {
    goActive()
    publishInfo()
  }

  override def onBecamePassive(): Unit = {
    goPassive()
    publishInfo()
  }

  override def onSubscribe: SubscribeHandler = super.onSubscribe orElse {
    case T_SIGNAL => None
  }



  def checkMatch(s: String, r: Option[Regex]) = r match {
    case None => true
    case Some(x) => x.findFirstMatchIn(s) match {
      case Some(m) => true
      case _ => false
    }
  }

  def checkLevel(l: Int, required: AlertLevel) = required.code <= l

  def forward(value: EventFrame) = {
    val expiry = value ++> 'expiryTs | 0
    if (isComponentActive && expiry < 1 || expiry >= now)
      T_SIGNAL !! Some(value)
  }

  def processSignal(value: EventFrame): Unit = {
    for (
      signal <- value %> 'signal;
      sClass <- signal ~> 'signalClass
    ) {
      val sSubclass = signal ~> 'signalSubclass | ""
      val sLevel = signal +> 'level | 0

      if (checkMatch(sClass, signalClassR) && checkMatch(sSubclass, signalSubclassR) && checkLevel(sLevel, level)) {
        forward(signal)
      }

    }

  }

  override def modelEntryInfo: Model = new DesktopNotificationsSubscriptionAvailable(entityId, self, name)

  private def goPassive() = {
    currentState = DesktopNotificationsSubscriptionStatePassive()
    logger.debug(s"Subscription stopped")
  }

  private def goActive() = {
    logger.debug(s"Subscription started")
    currentState = DesktopNotificationsSubscriptionStateActive(Some("ok"))
  }

  private def handler: Receive = {
    case f: EventFrame if isComponentActive => processSignal(f)
  }

  private def terminateSubscription(reason: Option[String]) = {
    goPassive()
  }
}


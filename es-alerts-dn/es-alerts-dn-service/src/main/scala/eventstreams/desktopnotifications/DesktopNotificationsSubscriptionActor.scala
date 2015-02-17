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
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import eventstreams.signals.SignalLevel
import play.api.libs.json.{JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz.{-\/, \/-}

trait DesktopNotificationsSubscriptionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "DesktopNotifications.Subscription"
}

object DesktopNotificationsSubscriptionActor {
  def props(id: String) = Props(new DesktopNotificationsSubscriptionActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


sealed trait DesktopNotificationsSubscriptionState {
  def details: Option[String]
}

case class DesktopNotificationsSubscriptionStateUnknown(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStateActive(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStatePassive(details: Option[String] = None) extends DesktopNotificationsSubscriptionState

case class DesktopNotificationsSubscriptionStateError(details: Option[String] = None) extends DesktopNotificationsSubscriptionState


class DesktopNotificationsSubscriptionActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider
  with DesktopNotificationsSubscriptionSysevents
  with WithSyseventPublisher {

  val T_SIGNAL = TopicKey("desktopnotification.signal")

  var name = "default"
  var created = prettyTimeFormat(now)
  var currentState: DesktopNotificationsSubscriptionState = DesktopNotificationsSubscriptionStateUnknown(Some("Initialising"))
  var level: SignalLevel = SignalLevel.default()
  var signalClass: String = "default"
  var signalSubclass: Option[String] = None
  var signalClassR: Option[Regex] = None
  var signalSubclassR: Option[Regex] = None
  var conflate: Boolean = true
  var autoCloseSec: Int = 10

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def onInitialConfigApplied(): Unit = context.parent ! DesktopNotificationsSubscriptionAvailable(key)

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  override def preStart(): Unit = {
    super.preStart()
  }

  def publishInfo() = {
    T_INFO !! info
  }

  def publishProps() = T_PROPS !! propsConfig

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

  def info = Some(Json.obj(
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


  override def becomeActive(): Unit = {
    goActive()
    publishInfo()
  }

  override def becomePassive(): Unit = {
    goPassive()
    publishInfo()
  }

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case T_SIGNAL => None
  }


  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the subscription")
          self ! BecomePassive()
          \/-(OK())
        case _ =>
          logger.info("Already stopped")
          -\/(Fail("Already stopped"))
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
          -\/(Fail("Already started"))
        case _ =>
          logger.info("Starting the subscription " + self.toString())
          self ! BecomeActive()
          \/-(OK())
      }
    case T_REMOVE =>
      terminateSubscription(Some("Subscription being deleted"))
      removeConfig()
      self ! PoisonPill
      \/-(OK())
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("No data");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  def goPassive() = {
    currentState = DesktopNotificationsSubscriptionStatePassive()
    logger.debug(s"Subscription stopped")
  }

  override def applyConfig(key: String, config: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    created = prettyTimeFormat(config ++> 'created | now)

    signalClass = config ~> 'signalClass | "default"
    signalClassR = Some(signalClass.r)
    signalSubclass = config ~> 'signalSubclass
    signalSubclassR = signalSubclass.map(_.r)
    level = SignalLevel.fromString(config ~> 'level | "Very low")
    conflate = config ?> 'conflate | true
    autoCloseSec = config +> 'autoClose | 10

  }

  override def afterApplyConfig(): Unit = {

    if (isComponentActive)
      goActive()
    else
      goPassive()

    publishProps()
    publishInfo()
  }


  def checkMatch(s: String, r: Option[Regex]) = r match {
    case None => true
    case Some(r) => r.findFirstMatchIn(s) match {
      case Some(m) => true
      case _ => false
    }
  }

  def checkLevel(l: Int, required: SignalLevel) = required.code <= l


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

      if (checkMatch(sClass, signalClassR) && checkMatch(sSubclass,  signalSubclassR) && checkLevel(sLevel, level)) {
        forward(signal)
      }

    }

  }

  private def handler: Receive = {
    case f: EventFrame if isComponentActive => processSignal(f)
  }

  private def terminateSubscription(reason: Option[String]) = {
    goPassive()
  }

  private def goActive() = {
    logger.debug(s"Subscription started")
    currentState = DesktopNotificationsSubscriptionStateActive(Some("ok"))
  }

}


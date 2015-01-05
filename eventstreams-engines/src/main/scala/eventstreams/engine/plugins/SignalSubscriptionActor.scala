/*
 * Copyright 2014 Intelix Pty Ltd
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

package eventstreams.engine.plugins

import akka.actor._
import eventstreams.core.Tools.configHelper
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.engine.signals.SignalLevel
import play.api.libs.json.{JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz.{-\/, \/-}

object SignalSubscriptionActor {
  def props(id: String) = Props(new SignalSubscriptionActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


sealed trait SignalSubscriptionState {
  def details: Option[String]
}

case class SignalSubscriptionStateUnknown(details: Option[String] = None) extends SignalSubscriptionState

case class SignalSubscriptionStateActive(details: Option[String] = None) extends SignalSubscriptionState

case class SignalSubscriptionStatePassive(details: Option[String] = None) extends SignalSubscriptionState

case class SignalSubscriptionStateError(details: Option[String] = None) extends SignalSubscriptionState


class SignalSubscriptionActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider {

  val T_SIGNAL = TopicKey("signal")

  var name = "default"
  var created = prettyTimeFormat(now)
  var currentState: SignalSubscriptionState = SignalSubscriptionStateUnknown(Some("Initialising"))
  var level: SignalLevel = SignalLevel.default()
  var signalClass: String = "default"
  var signalSubclass: Option[String] = None
  var signalClassR: Option[Regex] = None
  var signalSubclassR: Option[Regex] = None
  var conflate: Boolean = true
  var autoCloseSec: Int = 10

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def onInitialConfigApplied(): Unit = context.parent ! SignalSubscriptionAvailable(key)

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
    case SignalSubscriptionStateUnknown(_) => "unknown"
    case SignalSubscriptionStateActive(_) => "active"
    case SignalSubscriptionStatePassive(_) => "passive"
    case SignalSubscriptionStateError(_) => "error"
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

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case T_SIGNAL => None
  }


  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
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
    case T_KILL =>
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
    currentState = SignalSubscriptionStatePassive()
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


  def forward(value: JsValue) = {
    val expiry = value ++> 'expiryTs | 0
    if (isComponentActive && expiry < 1 || expiry >= now)
      T_SIGNAL !! Some(value)
  }

  def processSignal(value: JsValue): Unit = {
    for (
      signal <- value #> 'signal;
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
    case f: JsonFrame if isComponentActive => processSignal(f.event)
  }

  private def terminateSubscription(reason: Option[String]) = {
    goPassive()
  }

  private def goActive() = {
    logger.debug(s"Subscription started")
    currentState = SignalSubscriptionStateActive(Some("ok"))
  }

}


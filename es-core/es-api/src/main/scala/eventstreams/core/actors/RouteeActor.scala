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

package eventstreams.core.actors

import _root_.core.sysevents.SyseventOps.stringToSyseventOps
import akka.actor.ActorRef
import eventstreams._
import eventstreams.core.components.routing.MessageRouterActor
import play.api.libs.json.{JsString, JsValue, Json}

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}

trait DefaultTopicKeys {
  val T_ADD = TopicKey("add")
  val T_EDIT = TopicKey("edit")
  val T_LIST = TopicKey("list")
  val T_INFO = TopicKey("info")
  val T_STATS = TopicKey("stats")
  val T_PROPS = TopicKey("props")
  val T_START = TopicKey("start")
  val T_STOP = TopicKey("stop")
  val T_PURGE = TopicKey("purge")
  val T_REMOVE = TopicKey("remove")
  val T_RESET = TopicKey("reset")
  val T_UPDATE_PROPS = TopicKey("update_props")
  val T_REPLAY = TopicKey("replay")
  val T_CONFIGTPL = TopicKey("configtpl")
}

trait RouteeSysevents extends SubjectSubscriptionSysevents {
  val UnsupportedPayload = "Routee.UnsupportedPayload".warn
  val NewCommand = "Routee.NewCommand".trace
  val CommandFailed = "Routee.CommandFailed".warn
  val CommandSuccessful = "Routee.CommandSuccessful".trace
}

trait RouteeActor
  extends ActorWithLocalSubscribers
  with DefaultTopicKeys with RouteeSysevents {

  type SubscribeHandler = PartialFunction[TopicKey, Unit]
  type UnsubscribeHandler = PartialFunction[TopicKey, Unit]
  type CommandHandler = PartialFunction[TopicKey, \/[Fail, OK]]

  def key: ComponentKey
  implicit def stringToComponentKey(keyAsString: String): ComponentKey = ComponentKey(keyAsString)

  case class Publisher(key: TopicKey) {
    final def !!(data: Any) = data match {
      case Some(x) => x match {
        case t: JsValue => topicUpdate(key, Some(Json.stringify(t)))
        case t => topicUpdate(key, Some(t.toString))
      }
      case None => topicUpdate(key, None)
      case t: JsValue => topicUpdate(key, Some(Json.stringify(t)))
      case t => topicUpdate(key, Some(t.toString))
    }
    final def !!*(data: Any) = data match {
      case Some(x) => x match {
        case t: JsValue => topicUpdate(key, Some(Json.stringify(t)), canBeCached = false)
        case t => topicUpdate(key, Some(t.toString), canBeCached = false)
      }
      case None => topicUpdate(key, None, canBeCached = false)
      case t: JsValue => topicUpdate(key, Some(Json.stringify(t)), canBeCached = false)
      case t => topicUpdate(key, Some(t.toString), canBeCached = false)
    }
  }

  implicit def toPublisher(key: TopicKey): Publisher = Publisher(key)

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(key, self)
    super.preStart()
  }

  def allTopicKeys = allSubjects.map(_.topic)

  final def topicUpdate(topic: TopicKey, data: Option[String], singleTarget: Option[ActorRef] = None, canBeCached: Boolean = true): Unit =
    singleTarget match {
      case Some(ref) => updateTo(LocalSubj(key, topic), ref, data, canBeCached)
      case None => updateToAll(LocalSubj(key, topic), data, canBeCached)
    }

  final def genericCommandError(cmdTopicKey: TopicKey, replyToSubj: Option[Any], errorMessage: String, singleTarget: ActorRef = sender()) = {
    replyToSubj.foreach(
      cmdErrTo(_, singleTarget, Json.obj(
        "error" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> errorMessage
        ))))
  }

  final def genericCommandSuccess(cmdTopicKey: TopicKey, replyToSubj: Option[Any], message: Option[String], singleTarget: ActorRef = sender()) = {
    replyToSubj.foreach(
      cmdOkTo(_, singleTarget, Json.obj(
        "ok" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> JsString(message.getOrElse(""))
        ))))
  }

  def onSubscribe: SubscribeHandler = PartialFunction.empty

  def onUnsubscribe: UnsubscribeHandler = PartialFunction.empty

  def onCommand(maybeData: Option[JsValue]): CommandHandler = PartialFunction.empty

  override final def processSubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try {
    if (onSubscribe.isDefinedAt(subject.topic)) {
      onSubscribe(subject.topic)
    } else {
      Warning >> ('Message -> s"Unhandled topic subscribe", 'Topic -> subject.topic.key)
    }
  } match {
    case Failure(failure) =>
      Error >> ('Message -> s"Error while subscribing to $subject", 'Details -> failure)
    case Success(_) => ()
  }

  override final def processUnsubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try {
    if (onUnsubscribe.isDefinedAt(subject.topic)) {
      onUnsubscribe(subject.topic)
    } else {
      Warning >> ('Message -> s"Unhandled topic unsubscribe", 'Topic -> subject.topic.key)
    }
  } match {
    case Failure(failure) =>
      Error >> ('Message -> s"Error while unsubscribing from $subject", 'Details -> failure)
    case Success(_) => ()
  }

  override final def processCommand(subject: LocalSubj, replyToSubj: Option[Any], maybeData: Option[String]): Unit = {
    NewCommand >> ('Topic -> subject.topic.key, 'ReplyTo -> replyToSubj)

    Try{

      val func = onCommand(maybeData.map(Json.parse))

      if (func.isDefinedAt(subject.topic)) {
        func(subject.topic)
      } else {
        Warning >> ('Message -> s"Unhandled topic command", 'Topic -> subject.topic.key, 'Data -> maybeData)
        OK()
      }

    } match {
      case Failure(failure) =>
        genericCommandError(subject.topic, replyToSubj, "Invalid operation")
        CommandFailed >> ('Topic -> subject.topic.key, 'Error -> "Invalid operation")
      case Success(result) => result match {
        case -\/(fail) =>
          fail.message.foreach { msg =>
            genericCommandError(subject.topic, replyToSubj, msg)
          }
          CommandFailed >> ('Topic -> subject.topic.key, 'Error -> fail)
        case \/-(ok) =>
          ok.message.foreach { msg =>
            genericCommandSuccess(subject.topic, replyToSubj, Some(msg))
          }
          CommandSuccessful >> ('Topic -> subject.topic.key, 'Message -> ok)
      }
    }
  }
}

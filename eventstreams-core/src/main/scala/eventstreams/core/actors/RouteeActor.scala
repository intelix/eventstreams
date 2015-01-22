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

import akka.actor.ActorRef
import core.events.EventOps.stringToEventOps
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{ComponentKey, LocalSubj, RegisterComponent, TopicKey}
import eventstreams.core.{Fail, OK}
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
  val T_KILL = TopicKey("kill")
  val T_RESET = TopicKey("reset")
  val T_UPDATE_PROPS = TopicKey("update_props")
  val T_REPLAY = TopicKey("replay")
  val T_CONFIGTPL = TopicKey("configtpl")
}

trait RouteeEvents extends SubjectSubscriptionEvents {
  val UnsupportedPayload = "Routee.UnsupportedPayload".warn
  val NewCommand = "Routee.NewCommand".trace
  val CommandFailed = "Routee.CommandFailed".warn
  val CommandSuccessful = "Routee.CommandSuccessful".trace
}

trait RouteeActor
  extends ActorWithLocalSubscribers
  with DefaultTopicKeys with RouteeEvents {


  def key: ComponentKey

  case class Publisher(key: TopicKey) {
    def !!(data: Any) = data match {
      case Some(x) => x match {
        case t: JsValue => topicUpdate(key, Some(Json.stringify(t)))
        case t => topicUpdate(key, Some(t.toString))
      }
      case None => topicUpdate(key, None)
      case d => UnsupportedPayload >> ('Type -> d)
    }
  }

  implicit def toPublisher(key: TopicKey): Publisher = Publisher(key)

  override def preStart(): Unit = {
    MessageRouterActor.path ! RegisterComponent(key, self)
    super.preStart()
  }

  def topicUpdate(topic: TopicKey, data: Option[String], singleTarget: Option[ActorRef] = None): Unit =
    singleTarget match {
      case Some(ref) => updateTo(LocalSubj(key, topic), ref, data)
      case None => updateToAll(LocalSubj(key, topic), data)
    }

  def genericCommandError(cmdTopicKey: TopicKey, replyToSubj: Option[Any], errorMessage: String, singleTarget: ActorRef = sender()) = {
    replyToSubj.foreach(
      cmdErrTo(_, singleTarget, Json.obj(
        "error" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> errorMessage
        ))))
  }

  def genericCommandSuccess(cmdTopicKey: TopicKey, replyToSubj: Option[Any], message: Option[String], singleTarget: ActorRef = sender()) = {
    replyToSubj.foreach(
      cmdOkTo(_, singleTarget, Json.obj(
        "ok" -> Json.obj(
          "key" -> cmdTopicKey.key,
          "msg" -> JsString(message.getOrElse(""))
        ))))
  }

  def processTopicSubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicUnsubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {}

  def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = \/-(OK())

  override def processSubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try(processTopicSubscribe(sourceRef, subject.topic)) match {
    case Failure(failure) =>
      Error >> ('Message -> s"Error while subscribing to $subject", 'Details -> failure)
    case Success(_) => ()
  }

  override def processUnsubscribeRequest(sourceRef: ActorRef, subject: LocalSubj): Unit = Try(processTopicUnsubscribe(sourceRef, subject.topic)) match {
    case Failure(failure) =>
      Error >> ('Message -> s"Error while unsubscribing from $subject", 'Details -> failure)
    case Success(_) => ()
  }

  override def processCommand(subject: LocalSubj, replyToSubj: Option[Any], maybeData: Option[String]): Unit = {
    NewCommand >> ('Topic -> subject.topic.key, 'ReplyTo -> replyToSubj)

    Try(processTopicCommand(subject.topic, replyToSubj, maybeData.map(Json.parse))) match {
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

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

import akka.actor.{Actor, ActorRef}
import _root_.core.sysevents.SyseventOps.stringToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams._
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.HashSet
import scala.collection.mutable

trait SubjectSubscriptionSysevents extends ComponentWithBaseSysevents {
  val UpdateForSubject = "Routee.UpdateForSubject".trace
  val NewSubjectSubscription = "Routee.NewSubjectSubscription".trace
  val SubjectSubscriptionRemoved = "Routee.SubjectSubscriptionRemoved".trace
  val FirstSubjectSubscriber = "Routee.FirstSubjectSubscriber".trace
  val NoMoreSubjectSubscribers = "Routee.NoMoreSubjectSubscribers".trace
  val SubjectCmdOK = "Routee.SubjectCmdOK".trace
  val SubjectCmdError = "Routee.SubjectCmdError".trace
  val SubjectCmd = "Routee.SubjectCmd".trace
}

trait ActorWithSubscribers[T] extends ActorWithComposableBehavior with SubjectSubscriptionSysevents {
  _: WithSyseventPublisher =>

  private val subscribers: mutable.Map[T, Set[ActorRef]] = new mutable.HashMap[T, Set[ActorRef]]()

  override def commonBehavior: Actor.Receive = handleMessages orElse super.commonBehavior

  def processCommand(subject: T, replyToSubj: Option[Any], maybeData: Option[String]) = {}

  def processSubscribeRequest(ref: ActorRef, subject: T) = {}

  def processUnsubscribeRequest(ref: ActorRef, subject: T) = {}

  def firstSubscriber(subject: T) = {}

  def lastSubscriberGone(subject: T) = {}

  def collectSubjects(f: T => Boolean) = subscribers.collect { case (sub, set) if f(sub) => sub}

//  def collectSubscribers(f: T => Boolean) = subscribers.filter { case (sub, set) => f(sub)}

  def subscribersFor(subj: T) = subscribers.collectFirst { case (sub, set) if subjectMatch(sub, subj) => set}

  def updateToAll(subj: T, data: Option[String]) = subscribersFor(subj).foreach(_.foreach(updateTo(subj, _, data)))

  def updateTo(subj: T, ref: ActorRef, data: Option[String]) =
    data foreach { d =>
      ref ! Update(subj, d, canBeCached = true)
      UpdateForSubject >>('Subject -> subj, 'Target -> ref, 'Data -> d)
    }


  def cmdOkTo(subj: Any, ref: ActorRef, data: JsValue) = {
    SubjectCmdOK >>('Subject -> subj, 'Target -> ref)
    ref ! CommandOk(subj, Json.stringify(data))
  }

  def cmdTo(subj: Any, data: JsValue) = {
    val dataStr = Json.stringify(data)
    SubjectCmd >>('Subject -> subj, 'Data -> dataStr)
    MessageRouterActor.path ! Command(subj, None, Some(dataStr))
  }

  def cmdErrTo(subj: Any, ref: ActorRef, data: JsValue) = {
    SubjectCmdError >>('Subject -> subj, 'Target -> ref)
    ref ! CommandErr(subj, Json.stringify(data))
  }

  def convertSubject(subj: Any): Option[T]
  def subjectMatch(subj: T, otherSubj: T): Boolean

  private def isOneOfTheSubscribers(ref: ActorRef) = subscribers.values.exists(_.contains(ref))


  override def onTerminated(ref: ActorRef): Unit = {
    if (isOneOfTheSubscribers(ref)) removeSubscriber(ref)
    super.onTerminated(ref)
  }

  private def handleMessages: Receive = {
    case Subscribe(sourceRef, subj) => convertSubject(subj) foreach (addSubscriber(sourceRef, _))
    case Unsubscribe(sourceRef, subj) => convertSubject(subj) foreach (removeSubscriber(sourceRef, _))
    case Command(subj, replyToSubj, data) =>
      convertSubject(subj) foreach (processCommand(_, replyToSubj, data))

  }

  private def addSubscriber(ref: ActorRef, subject: T): Unit = {
    NewSubjectSubscription >>('Subject -> subject, 'Source -> ref)
    context.watch(ref)
    subscribers.get(subject) match {
      case None =>
        FirstSubjectSubscriber >> ('Subject -> subject)
        firstSubscriber(subject)
      case _ => ()
    }
    subscribers += (subject -> (subscribers.getOrElse(subject, new HashSet[ActorRef]()) + ref))
    processSubscribeRequest(ref, subject)
  }

  private def removeSubscriber(ref: ActorRef): Unit = {
    context.unwatch(ref)
    subscribers.collect {
      case (subj, set) if set contains ref => subj
    } foreach (removeSubscriber(ref, _))
  }

  private def removeSubscriber(ref: ActorRef, subject: T): Unit = {
    val refs: Set[ActorRef] = subscribers.getOrElse(subject, new HashSet[ActorRef]()) - ref
    SubjectSubscriptionRemoved >>('Subject -> subject, 'Target -> ref)
    if (refs.isEmpty) {
      subscribers -= subject
      NoMoreSubjectSubscribers >> ('Subject -> subject)
      lastSubscriberGone(subject)
    } else subscribers += (subject -> refs)
    processUnsubscribeRequest(ref, subject)
  }


}

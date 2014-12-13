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

package common.actors

import akka.actor.{Actor, ActorRef, Terminated}
import hq._
import play.api.libs.json.JsValue

import scala.collection.immutable.HashSet
import scala.collection.mutable

trait ActorWithSubscribers[T] extends ActorWithComposableBehavior {

  private val subscribers: mutable.Map[T, Set[ActorRef]] = new mutable.HashMap[T, Set[ActorRef]]()

  override def commonBehavior: Actor.Receive = handleMessages orElse super.commonBehavior

  def processCommand(ref: ActorRef, subject: T, replyToSubj: Option[Any], maybeData: Option[JsValue]) = {}

  def processSubscribeRequest(ref: ActorRef, subject: T) = {}

  def processUnsubscribeRequest(ref: ActorRef, subject: T) = {}

  def firstSubscriber(subject: T) = {}

  def lastSubscriberGone(subject: T) = {}

  def collectSubjects(f: T => Boolean) = subscribers.collect { case (sub, set) if f(sub) => sub}

  def collectSubscribers(f: T => Boolean) = subscribers.filter { case (sub, set) => f(sub)}

  def subscribersFor(subj: T) = subscribers.get(subj)

  def updateToAll(subj: T, data: Option[JsValue]) = subscribersFor(subj).foreach(_.foreach(updateTo(subj, _, data)))

  def updateTo(subj: T, ref: ActorRef, data: Option[JsValue]) = {
    logger.debug(s"Update for $subj -> $ref")
    data foreach (ref ! Update(self, subj, _, canBeCached = true))
  }

  def cmdOkTo(subj: Any, ref: ActorRef, data: JsValue) = {
    logger.debug(s"Cmd OK for $subj -> $ref")
    ref ! CommandOk(self, subj, data)
  }
  def cmdErrTo(subj: Any, ref: ActorRef, data: JsValue) = {
    logger.debug(s"Cmd ERR for $subj -> $ref")
    ref ! CommandErr(self, subj, data)
  }

  def convertSubject(subj: Any) : Option[T]

  private def isOneOfTheSubscribers(ref: ActorRef) = subscribers.values.exists(_.contains(ref))


  override def onTerminated(ref: ActorRef): Unit = {
    if (isOneOfTheSubscribers(ref)) removeSubscriber(ref)
    super.onTerminated(ref)
  }

  private def handleMessages: Receive = {
    case Subscribe(sourceRef, subj) => convertSubject(subj) foreach(addSubscriber(sourceRef, _))
    case Unsubscribe(sourceRef, subj) => convertSubject(subj) foreach(removeSubscriber(sourceRef, _))
    case Command(sourceRef, subj, replyToSubj, data) =>
      convertSubject(subj) foreach(processCommand(sourceRef, _, replyToSubj, data))

  }

  private def addSubscriber(ref: ActorRef, subject: T): Unit = {
    logger.info(s"New subscriber for $subject at $ref")
    context.watch(ref)
    subscribers.get(subject) match {
      case None =>
        logger.info(s"First subscriber for $subject: $ref")
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
    if (refs.isEmpty) {
      subscribers -= subject
      logger.info(s"No more subscribers for $subject: $ref")
      lastSubscriberGone(subject)
    } else subscribers += (subject -> refs)
    processUnsubscribeRequest(ref, subject)
  }


}

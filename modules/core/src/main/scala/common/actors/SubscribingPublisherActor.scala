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

import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import common.{JsonFrame, Stop}

import scala.annotation.tailrec
import scala.collection.mutable

trait SubscribingPublisherActor
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorSubscriber
  with ActorPublisher[JsonFrame] {

  private val queue = mutable.Queue[JsonFrame]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def pendingToDownstreamCount = queue.size

  @tailrec
  final def sendIfPossible(): Unit =
    if (isActive && totalDemand > 0) take() match {
      case None => ()
      case Some(x) =>
        logger.debug(s"!>> firing onNext - " + x)
        onNext(x)
        sendIfPossible()
    }

  def forwardToNext(value: JsonFrame) = {
    offer(value)
    sendIfPossible()
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    logger.debug("!>>>> post stop!")
  }

  def execute(value: JsonFrame): Option[Seq[JsonFrame]]

  private def offer(m: JsonFrame) = queue.enqueue(m)

  private def take() = if (queue.size > 0) Some(queue.dequeue()) else None

  private def stop(reason: Option[String]) = {
    logger.info(s"Shutting down subscribing publisher, reason given: $reason")
    onComplete()
    context.stop(self)
  }

  private def handler: Receive = {
    case Cancel => stop(Some("Cancelled"))
    case OnComplete => stop(Some("OnComplete"))
    case OnError(cause) => stop(Some("Error: " + cause.getMessage))
    case Stop(reason) => stop(reason)

    case OnNext(el: JsonFrame) =>
      logger.debug(s"!>> OnNext - $isActive :  $el")
      if (isActive) execute(el) foreach (_.foreach(forwardToNext))
    case Request(n) =>
      logger.debug(s"!>>> Request $n")
      sendIfPossible()

  }


}

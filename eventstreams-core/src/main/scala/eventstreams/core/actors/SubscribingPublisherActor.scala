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

package eventstreams.core.actors

import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.{JsonFrame, Stop}

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._


trait SubscribingPublisherEvents extends ComponentWithBaseEvents {
  val MessageArrived = 'MessageArrived.info
  val MessagePublished = 'MessagePublished.info
  val NewDemand = 'NewDemand.info
  val ClosingStream = 'ClosingStream.info
}


trait SubscribingPublisherActor
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorSubscriber
  with ActorPublisher[JsonFrame]
  with ActorWithTicks
  with SubscribingPublisherEvents {

  private val queue = mutable.Queue[JsonFrame]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def pendingToDownstreamCount = queue.size

  @tailrec
  final def sendIfPossible(): Unit =
    if (isActive && totalDemand > 0) take() match {
      case None => ()
      case Some(x) =>
        onNext(x)
        val currentDepth = queue.size
        MessagePublished >>('EventId --> x.eventIdOrNA, 'RemainingDemand --> totalDemand, 'PublisherQueueDepth --> currentDepth)
        sendIfPossible()
    }

  def forwardToNext(value: JsonFrame) = {
    offer(value)
    sendIfPossible()
  }

  def execute(value: JsonFrame): Option[Seq[JsonFrame]]

  private def offer(m: JsonFrame) = queue.enqueue(m)

  private def take() = if (queue.size > 0) Some(queue.dequeue()) else None

  private def stop(reason: Option[String]) = {
    ClosingStream >> ('Reason --> (reason | "none given"), 'PublisherQueueDepth --> pendingToDownstreamCount)
    onComplete()
    context.stop(self)
  }

  private def handler: Receive = {
    case Cancel => stop(Some("Cancelled"))
    case OnComplete => stop(Some("OnComplete"))
    case OnError(cause) => stop(Some("Error: " + cause.getMessage))
    case Stop(reason) => stop(reason)

    case OnNext(el: JsonFrame) =>
      MessageArrived >> ('EventId --> el.eventIdOrNA, 'PublisherQueueDepth --> pendingToDownstreamCount, 'StreamActive --> isActive)
      if (isActive) execute(el) foreach (_.foreach(forwardToNext))
    case Request(n) =>
      NewDemand >> ('Requested --> n, 'Total --> totalDemand)
      sendIfPossible()

  }

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    sendIfPossible()
  }
}

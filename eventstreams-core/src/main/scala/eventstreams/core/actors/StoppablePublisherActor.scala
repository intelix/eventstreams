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

import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.EventFrame
import eventstreams.core.Tools.configHelper
import play.api.libs.json.JsValue

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._

trait StandardPublisherEvents extends ComponentWithBaseEvents {
  val MessagePublished = 'MessagePublished.info
  val NewDemand = 'NewDemand.info
  val ClosingStream = 'ClosingStream.info
}

trait StoppablePublisherActor[T]
  extends ActorPublisher[T]
  with ActorWithComposableBehavior
  with Stoppable
  with PipelineWithStatesActor
  with StandardPublisherEvents
  with ActorWithTicks {

  this: WithEventPublisher =>

  override def commonBehavior: Receive = handlePublisherShutdown orElse super.commonBehavior

  private val queue = mutable.Queue[T]()

  def pendingToDownstreamCount = queue.size

  private def eventId(x: T): Option[String] = x match {
    case m: EventFrame => m.eventId
    case m => None
  }

  @tailrec
  final def sendIfPossible(): Unit =
    if (isActive && totalDemand > 0 && isComponentActive) {
      if (queue.size < totalDemand) produceAndOfferMore(totalDemand - queue.size)
      take() match {
        case None => ()
        case Some(x) =>
          onNext(x)
          val currentDepth = queue.size
          MessagePublished >>('EventId -> (eventId(x) | "n/a"), 'RemainingDemand -> totalDemand, 'PublisherQueueDepth -> currentDepth)
          sendIfPossible()
      }
    }


  def forwardToFlow(value: T) = {
    offer(value)
    sendIfPossible()
  }

  private def offer(m: T) = queue.enqueue(m)

  private def take() = if (queue.size > 0) Some(queue.dequeue()) else None

  def produceMore(count: Long): Option[Seq[T]] = None

  private def produceAndOfferMore(count: Long) = produceMore(count) foreach { seq => seq.foreach(offer)}

  override def stop(reason: Option[String]) = {
    ClosingStream >>('Reason -> (reason | "none given"), 'PublisherQueueDepth -> pendingToDownstreamCount)
    onComplete()
    super.stop(reason)
  }

  private def handlePublisherShutdown: Receive = {
    case Cancel => stop(Some("Cancelled"))
    case Request(n) =>
      NewDemand >>('Requested -> n, 'Total -> totalDemand)
      produceAndOfferMore(n)
      sendIfPossible()
  }


  override def becomeActive(): Unit = {
    super.becomeActive()
    sendIfPossible()
  }

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    sendIfPossible()
  }

}

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
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.EventFrame

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._

trait StandardPublisherSysevents extends ComponentWithBaseSysevents {
  val MessagePublished = 'MessagePublished.info
  val NewDemand = 'NewDemand.info
  val ClosingStream = 'ClosingStream.info
  val EndOfStreamPublished = 'EndOfStreamPublished.info
  val EndOfStreamWithErrorPublished = 'EndOfStreamWithErrorPublished.warn
}


trait StoppablePublisherActor[T]
  extends ActorPublisher[T]
  with ActorWithComposableBehavior
  with Stoppable
  with ActorWithActivePassiveBehaviors
  with StandardPublisherSysevents
  with ActorWithTicks {

  this: WithSyseventPublisher =>

  private val queue = mutable.Queue[StreamElement]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def pendingToDownstreamCount = queue.size

  def pushToStream(e: StreamElement) = {
    offer(e)
    deliverToDownstream()
  }

  def pushEndOfStream() = pushToStream(EndOfStream())

  def pushEndOfStreamWithError(error: String): Unit = pushEndOfStreamWithError(new Exception(error))

  def pushEndOfStreamWithError(error: Throwable): Unit = pushToStream(EndOfStreamWithError(error))

  def pushSingleEventToStream(value: T) = pushToStream(ScheduledEvent(value))

  def onRequestForMore(currentDemand: Int) = {}

  def currentDemand: Int = if (totalDemand < queue.size) 0 else (totalDemand - queue.size).toInt

  override def stop(reason: Option[String]) = {
    ClosingStream >>('Reason -> (reason | "none given"), 'PublisherQueueDepth -> pendingToDownstreamCount)
    if (isActive) onComplete()
    super.stop(reason)
  }

  override def onBecameActive(): Unit = {
    super.onBecameActive()
    self ! ProduceMore()
    deliverToDownstream()
  }

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    self ! ProduceMore()
    deliverToDownstream()
  }

  def isComponentAndStreamActive: Boolean = super.isComponentActive && isActive

  def onDataAvailable() = if (isComponentAndStreamActive && currentDemand > 0) onRequestForMore(currentDemand)

  private def eventId(x: T): Option[String] = x match {
    case m: EventFrame => m.eventId
    case m => None
  }

  @tailrec
  private final def deliverToDownstream(): Unit =
    if (totalDemand > 0 && isComponentAndStreamActive)
      take() match {
        case None => ()
        case Some(ScheduledEvent(x)) =>
          onNext(x)
          val currentDepth = queue.size
          MessagePublished >>('EventId -> (eventId(x) | "n/a"), 'RemainingDemand -> totalDemand, 'PublisherQueueDepth -> currentDepth)
          if (currentDepth == 0) self ! ProduceMore() else deliverToDownstream()
        case Some(EndOfStream()) =>
          onComplete()
          queue.clear()
          EndOfStreamPublished >>()
        case Some(EndOfStreamWithError(e)) =>
          onError(e)
          queue.clear()
          EndOfStreamWithErrorPublished >> ('Error -> e)
      }

  private def offer(m: StreamElement) = queue.enqueue(m)

  private def take() = if (queue.size > 0) Some(queue.dequeue()) else None

  private def handler: Receive = {
    case ProduceMore() =>
      if (isComponentAndStreamActive && currentDemand > 0) onRequestForMore(currentDemand)
    case Cancel =>
      stop(Some("Cancelled"))
    case Request(n) =>
      NewDemand >>('Requested -> n, 'Total -> totalDemand)
      if (isComponentAndStreamActive) onRequestForMore(currentDemand)
      deliverToDownstream()
  }

  sealed trait StreamElement

  case class ScheduledEvent(e: T) extends StreamElement

  case class EndOfStream() extends StreamElement

  case class EndOfStreamWithError(error: Throwable) extends StreamElement

  private case class ProduceMore()

}


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

import agent.shared.{Acknowledge, Acknowledgeable}
import akka.actor.ActorRef
import common.NowProvider

import scala.concurrent.duration.DurationInt

case class Acknowledged[T](correlationId: Long, msg: T)


trait AtLeastOnceDeliveryActor[T]
  extends ActorWithTicks
  with NowProvider {


  private var list = Vector[InFlight[T]]()
  private var counter = now

  override def commonBehavior: Receive = handleRedeliveryMessages orElse super.commonBehavior

  final def inFlightCount = list.size

  def configUnacknowledgedMessagesResendInterval = 5.seconds

  def canDeliverDownstreamRightNow: Boolean

  def getSetOfActiveEndpoints : Set[ActorRef]

  def fullyAcknowledged(correlationId: Long, msg: T)

  def deliverMessage(msg: T) = {
    val nextCorrelationId = correlationId(msg)
    list = list :+ send(InFlight[T](0, msg, nextCorrelationId, getSetOfActiveEndpoints, Set()))
    nextCorrelationId
  }


  private def filter() = {
    list = list.filter {
      case InFlight(_, msg, cId, endpoints, sentTo) if endpoints.isEmpty && sentTo.nonEmpty =>
        fullyAcknowledged(cId, msg)
        false
      case _ => true
    }
  }

  def acknowledgeUpTo(correlationId: Long, ackedByRef: ActorRef) = {
    logger.info(s"Ack: $correlationId from $ackedByRef")

    list = list.map {
      case InFlight(time, msg, cId, endpoints, sentTo) if cId <= correlationId && endpoints.contains(ackedByRef) =>
        InFlight[T](time, msg, cId, endpoints.filter(_ != ackedByRef), sentTo + ackedByRef)
      case other => other
    }

    filter()
  }

  override def internalProcessTick() = {
    resendAllPending()
    super.internalProcessTick()
  }

  def handleRedeliveryMessages: Receive = {
    case Acknowledge(x) => acknowledgeUpTo(x, sender())
  }

  private def correlationId(m: T): Long = {
    counter = counter + 1
    counter
  }

  private def resendAllPending() = {
    if (canDeliverDownstreamRightNow && list.nonEmpty) {
      logger.debug(s"Resending pending messages. Total inflight: $inFlightCount")
      list = for (
        next <- list
      ) yield resend(next)
    }
    filter()
  }

  private def resend(m: InFlight[T]): InFlight[T] = {
    if (canDeliverDownstreamRightNow && now - m.sentTime > configUnacknowledgedMessagesResendInterval.toMillis) {
      val inflight = send(m)
      logger.debug(s"Resending $inflight")
      inflight
    } else m
  }

  private def send(m: InFlight[T]): InFlight[T] = canDeliverDownstreamRightNow match {
    case true =>
      val activeEndpoints = getSetOfActiveEndpoints

      var remainingEndpoints = m.endpoints.filter(activeEndpoints.contains)

      if (remainingEndpoints.isEmpty && m.sentTo.isEmpty) remainingEndpoints = activeEndpoints

      remainingEndpoints.foreach { actor =>
        actor ! Acknowledgeable(m.msg, m.correlationId)
      }

      InFlight[T](now, m.msg, m.correlationId, remainingEndpoints, m.sentTo)
    case false => m
  }


}

private case class InFlight[T](sentTime: Long, msg: T, correlationId: Long, endpoints: Set[ActorRef], sentTo: Set[ActorRef])


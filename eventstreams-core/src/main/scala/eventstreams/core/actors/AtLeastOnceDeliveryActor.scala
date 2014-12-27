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

import akka.actor.ActorRef
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.NowProvider
import eventstreams.core.agent.core.{AcknowledgeAsProcessed, AcknowledgeAsReceived, Acknowledgeable}

import scala.concurrent.duration.DurationInt
import scala.util.Random

case class Acknowledged[T](correlationId: Long, msg: T)

trait AtLeastOnceDeliveryActorEvents extends ComponentWithBaseEvents {
  val ScheduledForDelivery = 'ScheduledForDelivery.info
  val DeliveryConfirmed = 'DeliveryConfirmed.info
  val ProcessingConfirmed = 'ProcessingConfirmed.info
  val DeliveryAttempt = 'DeliveryAttempt.trace
  val DeliveringToActor = 'DeliveringToActor.trace
}

trait AtLeastOnceDeliveryActor[T]
  extends ActorWithTicks
  with NowProvider
  with AtLeastOnceDeliveryActorEvents
  with WithEventPublisher {

  private var list = Vector[InFlight[T]]()
  private var counter = new Random().nextLong() // TODO replace counter with uuid + seq

  override def commonBehavior: Receive = handleRedeliveryMessages orElse super.commonBehavior

  final def inFlightCount = list.size

  def configUnacknowledgedMessagesResendInterval = 1.seconds

  def canDeliverDownstreamRightNow: Boolean

  def getSetOfActiveEndpoints: Set[ActorRef]

  def fullyAcknowledged(correlationId: Long, msg: T)

  def deliverMessage(msg: T) = {
    val nextCorrelationId = generateCorrelationId(msg)
    list = list :+ InFlight[T](0, msg, nextCorrelationId, getSetOfActiveEndpoints, Set(), Set(), 0)
    val depth = list.size
    ScheduledForDelivery >>('CorrelationId --> nextCorrelationId, 'DeliveryQueueDepth --> depth)
    deliverIfPossible()
    nextCorrelationId
  }


  private def filter() =
    list = list.filter {
      case m: InFlight[_] if m.endpoints.isEmpty && m.processedAck.nonEmpty =>
        fullyAcknowledged(m.correlationId, m.msg)
        false
      case _ => true
    }


  private def acknowledgeProcessed(correlationId: Long, ackedByRef: ActorRef) = {
    ProcessingConfirmed >>('CorrelationId --> correlationId, 'ConfirmedBy --> ackedByRef)

    list = list.map {
      case m: InFlight[_] if m.correlationId == correlationId =>
        m.copy[T](
          endpoints = m.endpoints.filter(_ != ackedByRef),
          processedAck = m.processedAck + ackedByRef,
          receivedAck = m.receivedAck + ackedByRef
        )
      case other => other
    }

    filter()
    deliverIfPossible()
  }

  private def acknowledgeReceived(correlationId: Long, ackedByRef: ActorRef) = {
    DeliveryConfirmed >>('CorrelationId --> correlationId, 'ConfirmedBy --> ackedByRef)

    list = list.map {
      case m: InFlight[_] if m.correlationId == correlationId =>
        m.copy[T](
          receivedAck = m.receivedAck + ackedByRef
        )
      case other => other
    }

    deliverIfPossible()
  }

  override def internalProcessTick() = {
    deliverIfPossible()
    super.internalProcessTick()
  }

  private def handleRedeliveryMessages: Receive = {
    case AcknowledgeAsProcessed(x) => acknowledgeProcessed(x, sender())
    case AcknowledgeAsReceived(x) => acknowledgeReceived(x, sender())
  }

  def generateCorrelationId(m: T): Long = {
    counter = counter + 1
    counter
  }

  def deliverIfPossible(forceResend: Boolean = false) =
    if (canDeliverDownstreamRightNow && list.nonEmpty) {
      list.find { m => m.endpoints.isEmpty || m.endpoints.exists(!m.receivedAck.contains(_))} foreach { toDeliver =>
        val newInflight = resend(toDeliver, forceResend)
        list = list.map {
          case m if m.correlationId == newInflight.correlationId => newInflight
          case other => other
        }
      }
      filter()
    }



  private def resend(m: InFlight[T], forceResend: Boolean = false): InFlight[T] =
    if (canDeliverDownstreamRightNow && (forceResend || now - m.sentTime > configUnacknowledgedMessagesResendInterval.toMillis)) {
      val inflight = send(m)
      DeliveryAttempt >>('CorrelationId --> inflight.correlationId, 'Attempt --> m.sendAttempts, 'Forced --> forceResend)
      inflight
    } else m


  private def send(m: InFlight[T]): InFlight[T] = canDeliverDownstreamRightNow match {
    case true =>
      val activeEndpoints = getSetOfActiveEndpoints

      var remainingEndpoints = m.endpoints.filter(activeEndpoints.contains)

      if (remainingEndpoints.isEmpty && m.processedAck.isEmpty) remainingEndpoints = activeEndpoints

      remainingEndpoints.filter(!m.receivedAck.contains(_)).foreach { actor =>
        DeliveringToActor >>('CorrelationId --> m.correlationId, 'Target --> actor)
        actor ! Acknowledgeable(m.msg, m.correlationId)
      }

      m.copy(
        sentTime = now, endpoints = remainingEndpoints, sendAttempts = m.sendAttempts + 1
      )

    case false => m
  }


}

private case class InFlight[T](
                                sentTime: Long,
                                msg: T,
                                correlationId: Long,
                                endpoints: Set[ActorRef],
                                processedAck: Set[ActorRef],
                                receivedAck: Set[ActorRef],
                                sendAttempts: Int)


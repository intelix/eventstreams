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
import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams._

import scala.concurrent.duration.DurationInt
import scala.util.Random

trait AtLeastOnceDeliveryActorSysevents extends ComponentWithBaseSysevents {
  val ScheduledForDelivery = 'ScheduledForDelivery.info
  val DeliveryConfirmed = 'DeliveryConfirmed.info
  val ProcessingConfirmed = 'ProcessingConfirmed.info
  val DeliveryAttempt = 'DeliveryAttempt.trace
  val DeliveringToActor = 'DeliveringToActor.trace
}

trait AtLeastOnceDeliveryActor[T <: WithID]
  extends ActorWithTicks
  with NowProvider
  with AtLeastOnceDeliveryActorSysevents
  with WithSyseventPublisher {

  private var unscheduledBatch: List[T] = List()
  private var batchId = new Random().nextLong().abs

  private var list = Vector[InFlight[Batch[T]]]()

  var inFlightCount: Int = 0

  override def commonBehavior: Receive = handleRedeliveryMessages orElse super.commonBehavior

  def configUnacknowledgedMessagesResendInterval = 1.seconds

  def configMaxBatchSize = 100

  def canDeliverDownstreamRightNow: Boolean

  def getSetOfActiveEndpoints: Set[ActorRef]

  def fullyAcknowledged(correlationId: Long, msg: Batch[T])

  def deliverMessage(msg: T): Long = {
    addToBatch(msg)

    val deliveryQueueDepth = list.size

    ScheduledForDelivery >>(
      'EntityId -> msg.entityId, 'CorrelationId -> batchId, 'EntityQueueDepth -> inFlightCount, 'DeliveryQueueDepth -> deliveryQueueDepth)

    deliverIfPossible()

    batchId
  }


  private def filter() =
    list = list.filter {
      case m: InFlight[_] if m.endpoints.isEmpty && m.processedAck.nonEmpty =>
        inFlightCount = inFlightCount - m.msg.entries.size
        fullyAcknowledged(m.correlationId, m.msg)
        false
      case _ => true
    }


  private def acknowledgeProcessed(correlationId: Long, ackedByRef: ActorRef) = {
    ProcessingConfirmed >>('CorrelationId -> correlationId, 'ConfirmedBy -> ackedByRef)

    list = list.map {
      case m: InFlight[_] if m.correlationId == correlationId =>
        m.copy[Batch[T]](
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
    DeliveryConfirmed >>('CorrelationId -> correlationId, 'ConfirmedBy -> ackedByRef)

    list = list.map {
      case m: InFlight[_] if m.correlationId == correlationId =>
        m.copy[Batch[T]](
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

  private def generateCorrelationId(): Long = {
    batchId = batchId + 1
    batchId
  }

  private def rollBatch() = {
    list = list :+ InFlight[Batch[T]](0, Batch(unscheduledBatch), batchId, getSetOfActiveEndpoints, Set(), Set(), 0)
    unscheduledBatch = List()
    batchId = generateCorrelationId()
  }


  private def addToBatch(msg: T) = {
    unscheduledBatch = unscheduledBatch :+ msg
    inFlightCount = inFlightCount + 1
    if (unscheduledBatch.size >= configMaxBatchSize) rollBatch()
  }

  def deliverIfPossible(forceResend: Boolean = false) =
    if (canDeliverDownstreamRightNow) {
      if (list.isEmpty && unscheduledBatch.nonEmpty) rollBatch()
      if (list.nonEmpty) {
        list.find { m => m.endpoints.isEmpty || m.endpoints.exists(!m.receivedAck.contains(_))} foreach { toDeliver =>
          val newInFlight = resend(toDeliver, forceResend)
          list = list.map {
            case m if m.correlationId == newInFlight.correlationId => newInFlight
            case other => other
          }
        }
        filter()
      }
    }


  private def resend(m: InFlight[Batch[T]], forceResend: Boolean = false): InFlight[Batch[T]] =
    if (canDeliverDownstreamRightNow && (forceResend || now - m.sentTime > configUnacknowledgedMessagesResendInterval.toMillis)) {
      val inflight = send(m)
      DeliveryAttempt >>('CorrelationId -> inflight.correlationId, 'Attempt -> m.sendAttempts, 'Forced -> forceResend)
      inflight
    } else m


  private def send(m: InFlight[Batch[T]]): InFlight[Batch[T]] = canDeliverDownstreamRightNow match {
    case true =>
      val activeEndpoints = getSetOfActiveEndpoints

      var remainingEndpoints = m.endpoints.filter(activeEndpoints.contains)

      if (remainingEndpoints.isEmpty && m.processedAck.isEmpty) remainingEndpoints = activeEndpoints

      remainingEndpoints.filter(!m.receivedAck.contains(_)).foreach { actor =>
        DeliveringToActor >>('CorrelationId -> m.correlationId, 'Target -> actor, 'BatchSize -> m.msg.entries.size)
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


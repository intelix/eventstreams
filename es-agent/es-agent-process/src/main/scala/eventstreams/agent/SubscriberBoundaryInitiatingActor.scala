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

package eventstreams.agent

import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import eventstreams.core.actors._
import eventstreams.gates.GateState
import eventstreams.{Batch, EventFrame, EventAndCursor}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.JsValue

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._

trait EventsourceSinkSysevents
  extends EventsourceActorSysevents
  with BaseActorSysevents with StateChangeSysevents with ReconnectingActorSysevents with GateMonitorEvents with AtLeastOnceDeliveryActorSysevents  {
  override def componentId: String = super.componentId + ".Sink"
  
  val MessageAcknowledged = 'MessageAcknowledged.trace
}

object SubscriberBoundaryInitiatingActor extends EventsourceSinkSysevents {
  def props(endpoint: String, maxInFlight: Int, maxBatchSize: Int)(implicit sysconfig: Config) =
    Props(new SubscriberBoundaryInitiatingActor(endpoint, maxInFlight, maxBatchSize))
}

class SubscriberBoundaryInitiatingActor(endpoint: String, maxInFlight: Int, maxBatchSize: Int)(implicit sysconfig: Config)
  extends ActorWithActivePassiveBehaviors
  with StoppableSubscriberActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with ActorWithGateStateMonitoring 
  with EventsourceSinkSysevents with WithSyseventPublisher {

  override val gateStateCheckInterval: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.agent.gate-check-interval") | 10.seconds

  override def reconnectAttemptInterval: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.agent.gate-reconnect-attempt-interval") | super.reconnectAttemptInterval
  override def remoteAssociationTimeout: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.agent.gate-handshake-timeout") | super.remoteAssociationTimeout

  override def commonBehavior: Receive = handleOnNext orElse super.commonBehavior

  override def connectionEndpoint: Option[String] = Some(endpoint)


  override def configMaxBatchSize: Int = maxBatchSize

  val correlationToCursor = mutable.Map[Long, JsValue]()
  
  var streamClosure: Option[Option[Throwable]] = None

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    startGateStateMonitoring()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    super.onDisconnectedFromEndpoint()
    stopGateStateMonitoring()
    if (isComponentActive) initiateReconnect()
  }

  override def onBecameActive(): Unit = {
    initiateReconnect()
  }

  override def onBecamePassive(): Unit = {
    stopGateStateMonitoring()
    disconnect()
  }

  override def onGateStateChanged(state: GateState): Unit = {
    deliverIfPossible()
    super.onGateStateChanged(state)
  }


  override def canDeliverDownstreamRightNow = isComponentActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: Batch[EventFrame]): Unit = {
    MessageAcknowledged >> ('CorrelationId -> correlationId)
    context.parent ! Acknowledged(correlationId, correlationToCursor.get(correlationId))
    correlationToCursor -= correlationId
    checkClosure()
  }

  private def checkClosure() = if (inFlightCount == 0) streamClosure match {
    case Some(Some(cause)) => context.parent ! StreamClosedWithError(Some(cause.getMessage))
    case Some(None) => context.parent ! StreamClosed()
    case _ => ()
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount
  }

  override def onStreamCompletedSuccessfully(): Unit = {
    streamClosure = Some(None)
    checkClosure()
  }
  override def onStreamCompletedWithError(cause: Throwable): Unit = {
    streamClosure = Some(Some(cause))
    checkClosure()
  }

  private def handleOnNext: Actor.Receive = {
    case OnNext(EventAndCursor(value, cursor)) =>
      val correlationId = deliverMessage(value)
      cursor.foreach(correlationToCursor += correlationId -> _)
    case OnNext(x) =>
      Error >> ('Message -> "unsupported payload", 'Payload -> x)
  }
}

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

package eventstreams.agent.flow

import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import eventstreams.core.JsonFrame
import eventstreams.core.actors._
import eventstreams.core.agent.core.ProducedMessage
import play.api.libs.json.JsValue

import scala.collection.mutable

trait DatasourceSinkEvents 
  extends DatasourceActorEvents with BaseActorEvents with StateChangeEvents with ReconnectingActorEvents {
  override def componentId: String = super.componentId + ".Sink"
  
  val MessageAcknowledged = 'MessageAcknowledged.trace
}

object SubscriberBoundaryInitiatingActor extends DatasourceSinkEvents {
  def props(endpoint: String) = Props(new SubscriberBoundaryInitiatingActor(endpoint))
}

class SubscriberBoundaryInitiatingActor(endpoint: String)
  extends PipelineWithStatesActor
  with StoppableSubscriberActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithGateStateMonitoring 
  with DatasourceSinkEvents with WithEventPublisher {

  override def commonBehavior: Receive = handleOnNext orElse super.commonBehavior

  override def connectionEndpoint: String = endpoint

  val correlationToCursor = mutable.Map[Long, JsValue]()

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    startGateStateMonitoring()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    super.onDisconnectedFromEndpoint()
    stopGateStateMonitoring()
    if (isComponentActive) initiateReconnect()
  }

  override def becomeActive(): Unit = {
    initiateReconnect()
  }

  override def becomePassive(): Unit = {
    stopGateStateMonitoring()
    disconnect()
  }

  override def canDeliverDownstreamRightNow = isComponentActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    MessageAcknowledged >> ('CorrelationId -> correlationId)
    context.parent ! Acknowledged(correlationId, correlationToCursor.get(correlationId))
    correlationToCursor -= correlationId
  }


  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(1000) {
    override def inFlightInternally: Int = inFlightCount
  }

  private def handleOnNext: Actor.Receive = {
    case OnNext(ProducedMessage(value, cursor)) =>
      val correlationId = deliverMessage(JsonFrame(value,Map()))
      cursor.foreach(correlationToCursor += correlationId -> _)
    case OnNext(x) =>
      Error >> ('Message -> "unsupported payload", 'Payload -> x)
  }
}

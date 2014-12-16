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

package agent.controller.flow

import agent.core.ProducedMessage
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import common.JsonFrame
import common.actors._
import play.api.libs.json.JsValue

import scala.collection.mutable

object SubscriberBoundaryInitiatingActor {
  def props(endpoint: String) = Props(new SubscriberBoundaryInitiatingActor(endpoint))
}

class SubscriberBoundaryInitiatingActor(endpoint: String)
  extends PipelineWithStatesActor
  with ShutdownableSubscriberActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithGateStateMonitoring {

  override def commonBehavior: Receive = handleOnNext orElse super.commonBehavior

  override def connectionEndpoint: String = endpoint

  val correlationToCursor = mutable.Map[Long, JsValue]()

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    logger.info("In connected state")
    startGateStateMonitoring()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    super.onDisconnectedFromEndpoint()
    logger.info("In disconnected state")
    stopGateStateMonitoring()
    if (isPipelineActive) initiateReconnect()
  }

  override def becomeActive(): Unit = {
    logger.info(s"Sink becoming active")
    initiateReconnect()
  }

  override def becomePassive(): Unit = {
    logger.info(s"Sink becoming passive")
    stopGateStateMonitoring()
    disconnect()
  }

  override def canDeliverDownstreamRightNow = isPipelineActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Fully acknowledged $correlationId")
    context.parent ! Acknowledged(correlationId, correlationToCursor.get(correlationId))
    correlationToCursor -= correlationId
  }


  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(1000) {
    override def inFlightInternally: Int = inFlightCount
  }

  private def handleOnNext: Actor.Receive = {
    case OnNext(ProducedMessage(value, cursor)) =>
      logger.info(s"Next: $value")
      val correlationid = deliverMessage(JsonFrame(value,Map()))
      cursor.foreach(correlationToCursor += correlationid -> _)
    case OnNext(x) =>
      logger.info(s"Next - unsupported payload: $x")
  }
}

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

package hq.flows.core

import akka.actor.{ActorRef, Props}
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import common.ToolExt.configHelper
import common.actors._
import common.{Fail, JsonFrame}
import hq.flows.core.Builder._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.\/

class GateInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "gate"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      address <- props ~> 'address \/> Fail(s"Invalid gate instruction configuration. Missing 'address' value. Contents: ${Json.stringify(props)}")
    ) yield GateInstructionActor.props(address, props)

}

private object GateInstructionActor {
  def props(address: String, config: JsValue) = Props(new GateInstructionActor(address, config))
}

private class GateInstructionActor(address: String, config: JsValue)
  extends SubscribingPublisherActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithGateStateMonitoring {

  val maxInFlight = config +> 'buffer | 1000;
  val blockingDelivery = config ?> 'blockingDelivery | true;
  private val condition = SimpleCondition.conditionOrAlwaysTrue(config ~> 'simpleCondition)

  override def connectionEndpoint: String = address


  override def preStart(): Unit = {
    super.preStart()
  }

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

  override def canDeliverDownstreamRightNow = isActive && isPipelineActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Fully acknowledged $correlationId")
    //    context.parent ! Acknowledged(correlationId, msg)
    if (blockingDelivery) forwardToNext(msg)
  }

  override def execute(value: JsonFrame): Option[Seq[JsonFrame]] = {
    // TODO log failed condition
    if (!condition.isDefined || condition.get.metFor(value).isRight) {
      deliverMessage(value)
      if (blockingDelivery) None else Some(List(value))
    } else {
      Some(List(value))
    }
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount + pendingToDownstreamCount
  }

}

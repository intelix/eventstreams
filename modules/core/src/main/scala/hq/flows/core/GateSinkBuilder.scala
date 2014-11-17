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

import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import common.ToolExt.configHelper
import common.actors._
import common.{BecomeActive, Fail, JsonFrame}
import hq.flows.core.Builder.SinkActorPropsType
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.{Scalaz, \/}

private[core] object GateSinkBuilder extends BuilderFromConfig[SinkActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue): \/[Fail, SinkActorPropsType] =
    for (
      name <- props ~> 'name \/> Fail(s"Invalid gate sink configuration. Missing 'name' value. Contents: ${Json.stringify(props)}")
    ) yield GateSinkActor.props(name)

}

private object GateSinkActor {
  def props(gate: String) = Props(new GateSinkActor(gate))
}

private class GateSinkActor(gate: String)
  extends PipelineWithStatesActor
  with ShutdownableSubscriberActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithGateStateMonitoring {

  override def commonBehavior: Receive = handleOnNext orElse super.commonBehavior

  override def connectionEndpoint: String = "/user/gates/" +gate  // TODO do properly


  override def preStart(): Unit = {
    self ! BecomeActive() // TODO !>>>> remove!!!
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

  override def canDeliverDownstreamRightNow = isPipelineActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Fully achnowledged $correlationId")
    context.parent ! Acknowledged(correlationId, msg)
  }


  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(96) {
    override def inFlightInternally: Int = inFlightCount
  }

  private def handleOnNext: Actor.Receive = {
    case OnNext(x) => x match {
      case m : JsonFrame => deliverMessage(m)
      case _ => logger.warn(s"Unrecognised message at gate sink: $x")
    }
  }
}

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

package eventstreams.plugins.essentials

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import core.events.EventOps.symbolToEventOps
import core.events.{FieldAndValue, WithEventPublisher}
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.Types._
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.agent.core.GateState
import eventstreams.core.instructions.InstructionConstants
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration.FiniteDuration
import scalaz.Scalaz._
import scalaz._


trait GateInstructionEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val GateInstructionInstance = 'GateInstructionInstance.info

  val ConnectedToGate = 'ConnectedToGate.info
  val DisconnectedFromGate = 'DisconnectedFromGate.warn

  val FullAcknowledgement = 'FullAcknowledgement.trace
  val ConditionNotMet = 'ConditionNotMet.trace

  override def componentId: String = "Instruction.Gate"
}

trait GateInstructionConstants
  extends InstructionConstants
  with GateInstructionEvents
  with StateChangeEvents
  with SubscribingPublisherActorEvents
  with AtLeastOnceDeliveryActorEvents
  with GateMonitorEvents {
  val CfgFAddress = "address"
  val CfgFBuffer = "buffer"
  val CfgFBlockingDelivery = "blockingDelivery"
  val CfgFGateCheckInterval = "gateCheckInterval"
  val CfgFCondition = "simpleCondition"

}

object GateInstructionConstants extends GateInstructionConstants


class GateInstruction extends BuilderFromConfig[InstructionType] with GateInstructionConstants with WithEventPublisher {
  val configId = "gate"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      address <- props ~> CfgFAddress \/> Fail(s"Invalid $configId instruction configuration. Missing '$CfgFAddress' value. Contents: ${Json.stringify(props)}")
    ) yield {
      val uuid = Utils.generateShortUUID
      Built >>('Address -> address, 'Config -> Json.stringify(props), 'InstructionInstanceId -> uuid)
      GateInstructionActor.props(uuid, address, props)
    }

}

private object GateInstructionActor {
  def props(uuid: String, address: String, config: JsValue) = Props(new GateInstructionActor(uuid, address, config))
}

private class GateInstructionActor(instructionId: String, address: String, config: JsValue)
  extends StoppableSubscribingPublisherActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithGateStateMonitoring
  with GateInstructionConstants {

  val maxInFlight = config +> CfgFBuffer | 1000
  val blockingDelivery = config ?> CfgFBlockingDelivery | true
  private val condition = SimpleCondition.conditionOrAlwaysTrue(config ~> CfgFCondition)


  override def gateStateCheckInterval: FiniteDuration =
    (config +> CfgFGateCheckInterval).map(FiniteDuration(_, TimeUnit.MILLISECONDS)) | super.gateStateCheckInterval

  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('Address -> address, 'InstructionInstanceId -> instructionId)

  override def connectionEndpoint: String = address


  override def preStart(): Unit = {
    super.preStart()
    GateInstructionInstance >>('Buffer -> maxInFlight, 'Condition -> (config ~> CfgFCondition | "none"))
  }

  override def onConnectedToEndpoint(): Unit = {
    ConnectedToGate >>()
    super.onConnectedToEndpoint()
    startGateStateMonitoring()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    DisconnectedFromGate >>()
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


  override def onGateStateChanged(state: GateState): Unit = {
    deliverIfPossible()
    super.onGateStateChanged(state)
  }

  override def canDeliverDownstreamRightNow = isActive && isComponentActive && connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    FullAcknowledgement >> ('CorrelationId -> correlationId)
    if (blockingDelivery) forwardToFlow(msg)
  }

  override def execute(value: JsonFrame): Option[Seq[JsonFrame]] = {
    if (!condition.isDefined || condition.get.metFor(value).isRight) {
      deliverMessage(value)
      if (blockingDelivery) None else Some(List(value))
    } else {
      ConditionNotMet >> ('EventId -> value.eventIdOrNA)
      Some(List(value))
    }
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount + pendingToDownstreamCount
  }

}

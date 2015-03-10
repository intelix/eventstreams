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

package eventstreams.desktopnotifications

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, Props}
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.instructions.Types
import Types._
import eventstreams.core.actors._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.\/

trait DesktopNotificationsSinkInstructionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "DesktopNotifications.Instruction.Sink"
}

class DesktopNotificationsSinkInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "desktopnotifications-sink"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      address <- props ~> 'signalmgrAddress \/> Fail(s"Invalid signal sink instruction configuration. Missing 'signalmgrAddress' value. Contents: ${Json.stringify(props)}")
    ) yield DesktopNotificationsSinkInstructionActor.props(address, props)

}

private object DesktopNotificationsSinkInstructionActor {
  def props(address: String, config: JsValue) = Props(new DesktopNotificationsSinkInstructionActor(address, config))
}

private class DesktopNotificationsSinkInstructionActor(address: String, config: JsValue)
  extends StoppableSubscribingPublisherActor
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with DesktopNotificationsSinkInstructionSysevents 
  with WithSyseventPublisher {

  val maxInFlight = config +> 'buffer | 1000
  private val condition = SimpleCondition.conditionOrAlwaysTrue(config ~> 'simpleCondition)

  override def connectionEndpoint: Option[String] = Some(address)


  override def preStart(): Unit = {
    super.preStart()
  }

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    logger.info("In connected state")
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    super.onDisconnectedFromEndpoint()
    logger.info("In disconnected state")
    if (isComponentActive) initiateReconnect()
  }

  override def onBecameActive(): Unit = {
    logger.info(s"Desktop notifications sink instruction becoming active")
    initiateReconnect()
  }

  override def onBecamePassive(): Unit = {
    logger.info(s"Desktop notifications sink instruction becoming passive")
    disconnect()
  }

  override def canDeliverDownstreamRightNow = isActive && isComponentActive && connected

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def execute(value: EventFrame): Option[Seq[EventFrame]] = {
    // TODO log failed condition
    if (!condition.isDefined || condition.get.metFor(value).isRight) {
      deliverMessage(value)
    }
    Some(List(value))
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount + pendingToDownstreamCount
  }

}

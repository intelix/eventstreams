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

import agent.shared.{Acknowledge, Acknowledgeable}
import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Request
import common.{JsonFrame, Fail}
import common.ToolExt.configHelper
import common.actors.{PipelineWithStatesActor, ReconnectingActor, ShutdownablePublisherActor, ActorWithComposableBehavior}
import hq.flows.core.Builder.TapActorPropsType
import hq.gates.RegisterSink
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

private[core] object GateInputBuilder extends BuilderFromConfig[TapActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue, maybeData: Option[Condition]): \/[Fail, TapActorPropsType] =
    for (
      name <- props ~> 'name \/> Fail(s"Invalid gate input configuration. Missing 'name' value. Contents: ${Json.stringify(props)}")
    ) yield GateActor.props(name)
}

private object GateActor {
  def props(name: String) = Props(new GateActor(name))

  def start(name: String)(implicit f: ActorRefFactory) = f.actorOf(props(name))
}

private class GateActor(name: String)
  extends ShutdownablePublisherActor[JsonFrame]
  with ReconnectingActor
  with PipelineWithStatesActor
  with ActorWithComposableBehavior {


  override def monitorConnectionWithDeathWatch: Boolean = true

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    switchToCustomBehavior(handlerWhenPassive)
    initiateReconnect()
    logger.info(s"About to start tap $name")
  }

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    remoteActorRef.foreach(_ ! RegisterSink(self))
  }

  override def onDisconnectedFromEndpoint(): Unit = super.onDisconnectedFromEndpoint()

  override def becomeActive(): Unit = {
    logger.info(s"Becoming active - new accepting messages from gate [$name]")
    switchToCustomBehavior(handlerWhenActive)
    super.becomeActive()
  }

  override def becomePassive(): Unit = {
    logger.info(s"Becoming passive - no longer accepting messages from gate [$name]")
    switchToCustomBehavior(handlerWhenPassive)
    super.becomePassive()
  }

  def handler: Receive = {
    case Request(n) => logger.debug(s"Downstream requested $n messages")
  }

  def handlerWhenActive: Receive = {
    case m @ Acknowledgeable(f:JsonFrame,i) =>
      if (totalDemand > 0) {
        logger.debug(s"New message at gate tap (demand $totalDemand) [$name]: $m - produced and acknowledged")
        onNext(f)
        sender ! Acknowledge(i)
      } else {
        logger.debug(s"New message at gate tap (demand $totalDemand) [$name]: $m - ignored, no demand")
      }
    case m: Acknowledgeable[_] => logger.warn(s"Unexpected message at tap [$name]: $m - ignored")
    case Request(n) => logger.debug(s"Downstream requested $n messages")
  }

  def handlerWhenPassive: Receive = {
    case m @ Acknowledgeable(f:JsonFrame,i) =>
      logger.debug(s"Not active, message ignored")
    case m: Acknowledgeable[_] => logger.warn(s"Unexpected message at tap [$name]: $m - ignored")
    case Request(n) => logger.debug(s"Downstream requested $n messages")
  }

  override def connectionEndpoint: String = "/user/gates/" + name  // TODO do it properly
}


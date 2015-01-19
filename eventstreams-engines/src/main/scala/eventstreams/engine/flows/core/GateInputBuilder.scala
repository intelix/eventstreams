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

package eventstreams.engine.flows.core

import akka.actor.{ActorRefFactory, Props}
import akka.stream.actor.ActorPublisherMessage.Request
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.Types.TapActorPropsType
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.agent.core.{AcknowledgeAsProcessed, AcknowledgeAsReceived, Acknowledgeable}
import eventstreams.engine.gate.RegisterSink
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

trait GateInputEvents extends ComponentWithBaseEvents with BaseActorEvents with StandardPublisherEvents {

  override def componentId: String = "Flow.GateInput"
}

private[core] object GateInputBuilder extends BuilderFromConfig[TapActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, TapActorPropsType] =
    for (
      address <- props ~> 'sourceGateName \/> Fail(s"Invalid gate input configuration. Missing 'sourceGateName' value. Contents: ${Json.stringify(props)}")
    ) yield GateInputActor.props(id | "default", address)
}

 object GateInputActor extends GateInputEvents{
  def props(id: String, address: String) = Props(new GateInputActor(id, address))

  def start(id: String, address: String)(implicit f: ActorRefFactory) = f.actorOf(props(id, address))
}

private class GateInputActor(id: String, address: String)
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[JsonFrame]
  with ReconnectingActor
  with PipelineWithStatesActor
  with ActorWithDupTracking
  with WithMetrics
  with GateInputEvents
  with WithEventPublisher {

  override lazy val metricBaseName: MetricName = MetricName("flow")

  val _rate = metrics.meter(s"$id.source")
  val buffer = 1024

  override def monitorConnectionWithDeathWatch: Boolean = true

  override def commonBehavior: Receive = handlerWhenPassive orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    switchToCustomBehavior(handlerWhenPassive)
    initiateReconnect()
  }

  override def onConnectedToEndpoint(): Unit = {
    super.onConnectedToEndpoint()
    remoteActorRef.foreach(_ ! RegisterSink(self))
  }

  override def onDisconnectedFromEndpoint(): Unit = super.onDisconnectedFromEndpoint()

  override def becomeActive(): Unit = {
    switchToCustomBehavior(handlerWhenActive)
    super.becomeActive()
  }

  override def becomePassive(): Unit = {
    switchToCustomBehavior(handlerWhenPassive)
    super.becomePassive()
  }

  def handlerWhenActive: Receive = {
    case m @ Acknowledgeable(f:JsonFrame,i) =>
      if (pendingToDownstreamCount < buffer || pendingToDownstreamCount < totalDemand) {
        if (!isDup(sender(), m.id)) {
          sender() ! AcknowledgeAsReceived(i)
          _rate.mark()
          forwardToFlow(f)
          sender() ! AcknowledgeAsProcessed(i)
        } else {
          sender() ! AcknowledgeAsReceived(i)
        }
      }
  }

  def handlerWhenPassive: Receive = {
    case m @ Acknowledgeable(f:JsonFrame,i) => ()
  }

  override def connectionEndpoint: String = address
}


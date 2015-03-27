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

package eventstreams.flows.internal

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRefFactory, Props}
import eventstreams.Tools.{configHelper, optionsHelper}
import eventstreams._
import eventstreams.core.actors._
import eventstreams.gates.RegisterSink
import eventstreams.instructions.Types.TapActorPropsType
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

trait GateInputSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with StandardPublisherSysevents {

  override def componentId: String = "Flow.GateInput"
}

private[internal] object GateInputBuilder extends BuilderFromConfig[TapActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, TapActorPropsType] =
    for (
      address <- props ~> 'sourceGateName orFail s"Invalid gate input configuration. Missing 'sourceGateName' value. Contents: ${Json.stringify(props)}"
    ) yield GateInputActor.props(id | "default", address)
}

 object GateInputActor extends PassiveInputSysevents{
  def props(id: String, address: String) = Props(new GateInputActor(id, address))

  def start(id: String, address: String)(implicit f: ActorRefFactory) = f.actorOf(props(id, address))
}

private class GateInputActor(id: String, address: String)
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[EventFrame]
  with ReconnectingActor
  with ActorWithActivePassiveBehaviors
  with ActorWithDupTracking
  with WithCHMetrics
  with PassiveInputSysevents
  with WithSyseventPublisher {


  val _rate = metricRegistry.meter(s"flow.$id.source")
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

  override def onBecameActive(): Unit = {
    switchToCustomBehavior(handlerWhenActive)
    super.onBecameActive()
  }

  override def onBecamePassive(): Unit = {
    switchToCustomBehavior(handlerWhenPassive)
    super.onBecamePassive()
  }

  def forward(m: Any) = m match {
    case m: EventFrame => pushSingleEventToStream(m)
    case _ => ()
  }

  def handlerWhenActive: Receive = {
    case Acknowledgeable(m,id) =>
      if (pendingToDownstreamCount < buffer || pendingToDownstreamCount < totalDemand) {
        if (!isDup(sender(), id)) {
          sender() ! AcknowledgeAsReceived(id)
          sender() ! AcknowledgeAsProcessed(id)
          _rate.mark()

          m match {
            case x: Batch[_] => x.entries.foreach(forward)
            case x => forward(x)
          }

        } else {
          sender() ! AcknowledgeAsProcessed(id)
        }
      }
  }

  def handlerWhenPassive: Receive = {
    case m: Acknowledgeable[_] => ()
  }

  override def connectionEndpoint: Option[String] = Some(address)
}


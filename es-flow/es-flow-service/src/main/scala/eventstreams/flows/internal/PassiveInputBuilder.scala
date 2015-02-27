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
import eventstreams._
import eventstreams.core.actors._
import eventstreams.instructions.Types.TapActorPropsType
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

trait PassiveInputSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with StandardPublisherSysevents {

  override def componentId: String = "Flow.Input"
}

private[internal] object PassiveInputBuilder extends BuilderFromConfig[TapActorPropsType] {
  val configId = "gate"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, TapActorPropsType] =
    PassiveInputActor.props(id | "default").right
}

 object PassiveInputActor extends PassiveInputSysevents{
  def props(id: String) = Props(new PassiveInputActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id))
}

private class PassiveInputActor(id: String)
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[EventFrame]
  with ActorWithActivePassiveBehaviors
  with ActorWithDupTracking
  with WithMetrics
  with PassiveInputSysevents
  with WithSyseventPublisher {

  override lazy val metricBaseName: MetricName = MetricName("flow")

  val _rate = metrics.meter(s"$id.source")
  val buffer = 1024

  override def commonBehavior: Receive = handlerWhenPassive orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceId -> id)

  override def preStart(): Unit = {
    super.preStart()
    switchToCustomBehavior(handlerWhenPassive)
  }


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
    case m: Acknowledgeable[_] =>      ()
  }

}


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
import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import eventstreams._
import eventstreams.core.actors._
import eventstreams.instructions.Types.SinkActorPropsType
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

trait BlackHoleSinkSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with StandardSubscriberSysevents {
  override def componentId: String = "Flow.BlackHole"
}

private[internal] object BlackHoleSinkBuilder extends BuilderFromConfig[SinkActorPropsType] {
  val configId = "blackhole"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, SinkActorPropsType] =
    \/-(BlackholeAutoAckSinkActor.props(id))

}

 object BlackholeAutoAckSinkActor extends BlackHoleSinkSysevents {
  def props(id: Option[String]) = Props(new BlackholeAutoAckSinkActor(id))
}

private class BlackholeAutoAckSinkActor(maybeId: Option[String])
  extends ActorWithComposableBehavior
  with StoppableSubscriberActor
  with ActorWithActivePassiveBehaviors
  with WithMetrics
  with BlackHoleSinkSysevents
  with WithSyseventPublisher {

  override lazy val metricBaseName: MetricName = MetricName("flow")
  val id = maybeId | "default"
  val _rate = metrics.meter(s"$id.sink")

  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    self ! BecomeActive()
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(EventAndCursor(v, c)) =>
      MessageArrived >>('EventId -> v.eventIdOrNA, 'Contents -> Json.stringify(v.asJson))
      _rate.mark()
//      context.parent ! Acknowledged[Option[JsValue]](-1, c)
    case OnNext(EventFrame(v)) =>
      MessageArrived >>('EventId -> EventFrame(v).eventIdOrNA, 'Contents -> Json.stringify(EventFrame(v).asJson))
      _rate.mark()
//      context.parent ! Acknowledged[Option[JsValue]](-1, None)
    case OnNext(msg) =>
      MessageArrived >>('Contents -> msg)
      _rate.mark()
//      context.parent ! Acknowledged[Option[JsValue]](-1, None)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}
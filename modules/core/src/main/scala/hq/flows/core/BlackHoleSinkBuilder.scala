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

import agent.core.ProducedMessage
import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import common.{WithMetrics, BecomeActive, Fail}
import common.actors.{Acknowledged, ActorWithComposableBehavior, PipelineWithStatesActor, ShutdownableSubscriberActor}
import hq.flows.core.Builder.SinkActorPropsType
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.JsValue

import scalaz._
import Scalaz._

private[core] object BlackHoleSinkBuilder extends BuilderFromConfig[SinkActorPropsType] {
  val configId = "blackhole"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, SinkActorPropsType] =
    \/-(BlackholeAutoAckSinkActor.props(id))

}

private object BlackholeAutoAckSinkActor {
  def props(id: Option[String]) = Props(new BlackholeAutoAckSinkActor(id))
}

private class BlackholeAutoAckSinkActor(maybeId: Option[String])
  extends ActorWithComposableBehavior
  with ShutdownableSubscriberActor
  with PipelineWithStatesActor
  with WithMetrics {

  val id = maybeId | "default"

  override lazy val metricBaseName: MetricName = MetricName("flow")

  val _rate = metrics.meter(s"$id.sink")

  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    logger.info(s"!>>> Starting black hole")
    super.preStart()
    self ! BecomeActive()
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(ProducedMessage(v,c)) =>
      logger.debug(s"Sent into the black hole: $v")
      _rate.mark()
      context.parent ! Acknowledged[Option[JsValue]](-1, c)
    case OnNext(msg) =>
      logger.debug(s"Sent into the black hole: $msg")
      _rate.mark()
      context.parent ! Acknowledged[Option[JsValue]](-1, None)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}
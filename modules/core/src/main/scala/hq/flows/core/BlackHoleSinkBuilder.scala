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

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import common.{BecomeActive, Fail}
import common.actors.{Acknowledged, ActorWithComposableBehavior, PipelineWithStatesActor, ShutdownableSubscriberActor}
import hq.flows.core.Builder.SinkActorPropsType
import play.api.libs.json.JsValue

import scalaz.{\/, \/-}

private[core] object BlackHoleSinkBuilder extends BuilderFromConfig[SinkActorPropsType] {
  val configId = "blackhole"

  override def build(props: JsValue, maybeData: Option[Condition]): \/[Fail, SinkActorPropsType] =
    \/-(BlackholeAutoAckSinkActor.props)

}

private object BlackholeAutoAckSinkActor {
  def props = Props(new BlackholeAutoAckSinkActor())
}

private class BlackholeAutoAckSinkActor
  extends ActorWithComposableBehavior
  with ShutdownableSubscriberActor with PipelineWithStatesActor {


  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    logger.info(s"!>>> Starting black hole")
    super.preStart()
    self ! BecomeActive()
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) =>
      logger.debug(s"Sent into the black hole: $msg")
      context.parent ! Acknowledged[Any](-1, msg)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}
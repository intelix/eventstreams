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

package eventstreams.agent.flow

import akka.actor.{Actor, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.{RequestStrategy, WatermarkRequestStrategy, ZeroRequestStrategy}
import eventstreams.core.actors.{Acknowledged, ActorWithComposableBehavior, PipelineWithStatesActor, ShutdownableSubscriberActor}

object BlackholeAutoAckSinkActor {
  def props = Props(new BlackholeAutoAckSinkActor())
}

class BlackholeAutoAckSinkActor
  extends ActorWithComposableBehavior
  with ShutdownableSubscriberActor with PipelineWithStatesActor {


  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) => context.parent ! Acknowledged[Any](-1, msg)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}
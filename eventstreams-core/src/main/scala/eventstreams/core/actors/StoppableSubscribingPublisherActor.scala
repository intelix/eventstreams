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

package eventstreams.core.actors

import akka.stream.actor.ActorSubscriberMessage.OnNext
import core.events.WithEventPublisher
import eventstreams.core.JsonFrame

trait SubscribingPublisherActorEvents extends StandardSubscriberEvents with StandardPublisherEvents

trait StoppableSubscribingPublisherActor
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with StoppablePublisherActor[JsonFrame]
  with StoppableSubscriberActor
  with SubscribingPublisherActorEvents {

  this: WithEventPublisher =>
  
  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def execute(value: JsonFrame): Option[Seq[JsonFrame]]

  private def handler: Receive = {
    case OnNext(el: JsonFrame) =>
      MessageArrived >>('EventId -> el.eventIdOrNA, 'PublisherQueueDepth -> pendingToDownstreamCount, 'StreamActive -> isActive)
      if (isActive) execute(el) foreach (_.foreach(forwardToFlow))

  }

}

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

package hq.agents

import agent.shared._
import akka.actor._
import akka.remote.DisassociatedEvent
import common.actors.{PipelineWithStatesActor, SingleComponentActor}
import hq.{ComponentKey, TopicKey}
import play.api.libs.json.{JsArray, JsValue, Json}


object TapProxyActor {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new TapProxyActor(key, ref))
}

class TapProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with SingleComponentActor {


  private var info: Option[JsValue] = None

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! TapAvailable(key)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
  }

  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_START => ref ! OpenTap()
    case T_STOP => ref ! CloseTap()
    case T_KILL => ref ! RemoveTap()
  }

  private def process(json: JsValue) = {
    (json \ "info").asOpt[JsValue] foreach processInfo
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received agent info update: $info")
    topicUpdate(T_INFO, info)
  }


  private def commonMessageHandler: Receive = {
    case GenericJSONMessage(jsonString) =>
      Json.parse(jsonString).asOpt[JsValue] foreach process
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        self ! PoisonPill
      }
  }




}

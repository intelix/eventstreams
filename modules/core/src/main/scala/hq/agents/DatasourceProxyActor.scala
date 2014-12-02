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

import agent.controller.AgentMessagesV1.{DatasourceConfig, DatasourceInfo}
import agent.shared._
import akka.actor._
import akka.remote.DisassociatedEvent
import common.{OK, BecomePassive, BecomeActive}
import common.actors.{ActorWithDisassociationMonitor, PipelineWithStatesActor, SingleComponentActor}
import hq.{ComponentKey, TopicKey}
import play.api.libs.json.{JsArray, JsValue, Json}

import scalaz.\/-


object DatasourceProxyActor {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new DatasourceProxyActor(key, ref))
}

class DatasourceProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with ActorWithDisassociationMonitor
  with SingleComponentActor {


  private var info: Option[JsValue] = None
  private var props: Option[JsValue] = None

  private def publishInfo() = T_INFO !! info
  private def publishProps() = T_PROPS !! props

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! DatasourceProxyAvailable(key)
    logger.debug(s"Datasource proxy $key started, pointing at $ref")
    super.preStart()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
  }

  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_START =>
      ref ! BecomeActive()
      \/-(OK())
    case T_STOP =>
      ref ! BecomePassive()
      \/-(OK())
    case T_KILL =>
      ref ! RemoveTap()
      \/-(OK())
    case T_RESET =>
      ref ! ResetTapState()
      \/-(OK())
    case T_UPDATE_PROPS =>
      maybeData.foreach { data => ref !  ReconfigureTap(data) }
      \/-(OK(message = Some("Successfully reconfigured")))
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received agent info update: $info")
    publishInfo()
  }

  private def processConfig(json: JsValue) = {
    props = Some(json)
    logger.debug(s"Received agent props update: $info")
    publishProps()
  }


  private def commonMessageHandler: Receive = {
    case DatasourceInfo(json) => processInfo(json)
    case DatasourceConfig(json) => processConfig(json)
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        context.stop(self)
      }
  }




}

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

package eventstreams.engine.agents

import akka.actor._
import akka.remote.DisassociatedEvent
import core.events.EventOps.symbolToEventOps
import core.events.ref.ComponentWithBaseEvents
import core.events.{FieldAndValue, WithEventPublisher}
import eventstreams.core.actors.{ActorWithDisassociationMonitor, BaseActorEvents, PipelineWithStatesActor, RouteeActor}
import eventstreams.core.agent.core.{CommunicationProxyRef, ReconfigureTap, RemoveTap, ResetTapState}
import eventstreams.core.ds.AgentMessagesV1
import eventstreams.core.ds.AgentMessagesV1.{DatasourceConfig, DatasourceInfo}
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{BecomeActive, BecomePassive, OK}
import play.api.libs.json.JsValue

import scalaz.\/-

trait DatasourceProxyEvents extends ComponentWithBaseEvents with BaseActorEvents {
  override def componentId: String = "Actor.DatasourceProxy"

  val InfoUpdate = 'InfoUpdate.info
  val ConfigUpdate = 'ConfigUpdate.info

}

object DatasourceProxyActor extends DatasourceProxyEvents {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new DatasourceProxyActor(key, ref))
}

class DatasourceProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with ActorWithDisassociationMonitor
  with RouteeActor
  with DatasourceProxyEvents
  with WithEventPublisher {


  private var info: Option[JsValue] = None
  private var props: Option[JsValue] = None

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior


  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('Key -> key, 'RemoteActor -> ref)

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! DatasourceProxyAvailable(key)
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
      maybeData.foreach { data => ref ! ReconfigureTap(data)}
      \/-(OK(message = Some("Successfully reconfigured")))
  }

  private def publishInfo() = T_INFO !! info

  private def publishProps() = T_PROPS !! props

  private def processInfo(json: JsValue) = {
    info = Some(json)
    InfoUpdate >> ('Data -> json)
    publishInfo()
  }

  private def processConfig(json: JsValue) = {
    props = Some(json)
    ConfigUpdate >> ('Data -> json)
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

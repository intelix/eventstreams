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

package eventstreams.agent

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import _root_.core.sysevents.{FieldAndValue, WithSyseventPublisher}
import akka.actor._
import akka.remote.DisassociatedEvent
import eventstreams._
import eventstreams.agent.AgentMessagesV1.{EventsourceConfig, EventsourceInfo}
import eventstreams.core.actors.{ActorWithDisassociationMonitor, BaseActorSysevents, PipelineWithStatesActor, RouteeActor}
import play.api.libs.json.{JsValue, Json}

import scalaz.\/-

trait EventsourceProxySysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "Agent.EventsourceProxy"

  val InfoUpdate = 'InfoUpdate.info
  val ConfigUpdate = 'ConfigUpdate.info

}

object EventsourceProxyActor extends EventsourceProxySysevents {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new EventsourceProxyActor(key, ref))
}

class EventsourceProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with ActorWithDisassociationMonitor
  with RouteeActor
  with EventsourceProxySysevents
  with WithSyseventPublisher {


  private var info: Option[JsValue] = None
  private var props: Option[JsValue] = None

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior


  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> key.key, 'RemoteActor -> ref)

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! EventsourceProxyAvailable(key)
    super.preStart()
  }

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
  }

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_START =>
      ref ! BecomeActive()
      \/-(OK())
    case T_STOP =>
      ref ! BecomePassive()
      \/-(OK())
    case T_REMOVE =>
      ref ! RemoveEventsource()
      \/-(OK())
    case T_RESET =>
      ref ! ResetEventsourceState()
      \/-(OK())
    case T_UPDATE_PROPS =>
      maybeData.foreach { data => ref ! ReconfigureEventsource(Json.stringify(data))}
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
    case EventsourceInfo(json) => processInfo(json)
    case EventsourceConfig(json) => processConfig(json)
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        context.stop(self)
      }
  }


}

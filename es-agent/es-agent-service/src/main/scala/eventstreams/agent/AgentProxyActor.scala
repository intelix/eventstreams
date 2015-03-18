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
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.agent.AgentMessagesV1.{AgentEventsourceConfigs, AgentEventsources, AgentInfo}
import eventstreams.core.actors._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._

trait AgentProxyActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with RouteeSysevents {
  override def componentId: String = "Agent.Proxy"

  val InfoUpdate = 'InfoUpdate.info
  val AvailableEventsourcesUpdate = 'AvailableEventsourcesUpdate.info
  val ActiveEventsourcesUpdate = 'ActiveEventsourcesUpdate.info

  val TerminatingEventsourceProxy = 'TerminatingEventsourceProxy.info

  val EventsourceProxyUp = 'EventsourceProxyUp.info

  
}


object AgentProxyActor extends AgentProxyActorSysevents {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new AgentProxyActor(key, ref))
}

case class EventsourceProxyAvailable(key: ComponentKey)


class AgentProxyActor(val key: ComponentKey, ref: ActorRef)
  extends ActorWithActivePassiveBehaviors
  with ActorWithDisassociationMonitor
  with RouteeActor
  with AgentProxyActorSysevents
  with WithSyseventPublisher {


  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> key.key, 'RemoteActor -> ref)

  private var info: Option[JsValue] = None
  private var eventsourceConfigs: Option[JsValue] = None
  private var eventsourceProxies = List[EventsourceProxyMeta]()

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! AgentProxyAvailable(key)
    super.preStart()
  }

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_INFO => publishInfo()
    case T_LIST => publishEventsources()
    case T_CONFIGTPL => publishConfigs()
  }

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_ADD =>
      maybeData.foreach { j => ref ! CreateEventsource(Json.stringify(j)) }
      OK()
  }

  override def onTerminated(ref: ActorRef): Unit = {
    eventsourceProxies = eventsourceProxies.filter(_.ref != ref)
    publishEventsources()

    super.onTerminated(ref)
  }

  private def publishInfo() = T_INFO !! info

  private def publishConfigs() = T_CONFIGTPL !! eventsourceConfigs

  private def publishEventsources() = T_LIST !! Some(Json.toJson(eventsourceProxies.collect {
    case EventsourceProxyMeta(_, _, k, true) => Json.obj("ckey" -> k.key)
  }))

  private def commonMessageHandler: Receive = {
    case EventsourceProxyAvailable(x) =>
      eventsourceProxies = eventsourceProxies.map {
        case b@EventsourceProxyMeta(i, _, k, false) if k == x =>
          EventsourceProxyUp >> ('EventsourceProxyId -> i, 'EventsourceProxyKey -> k)
          b.copy(confirmed = true)
        case v => v
      }
      publishEventsources()

    case AgentInfo(jsValue) => processInfo(jsValue)
    case AgentEventsourceConfigs(jsValue) => processEventsourceConfigs(jsValue)
    case AgentEventsources(list) => processListOfSources(list)

    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        context.stop(self)
      }

  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    InfoUpdate >> ('Data -> json)
    publishInfo()
  }

  private def processEventsourceConfigs(json: JsValue) = {
    eventsourceConfigs = json #> 'eventsourceConfigSchema
    AvailableEventsourcesUpdate >> ('Data -> json)
    publishConfigs()
  }

  private def processListOfSources(list: List[EventsourceRef]) = {
    ActiveEventsourcesUpdate >> ('List -> list)

    eventsourceProxies.filterNot { d => list.exists(_.id == d.id)} foreach { dsToKill =>
      TerminatingEventsourceProxy >> ('EventsourceProxyId -> dsToKill.id, 'EventsourceProxyKey -> dsToKill.key, 'Reason -> "No longer active")
      dsToKill.ref ! PoisonPill
    }

    list.foreach { ds =>
      if (!eventsourceProxies.exists(_.id == ds.id)) {
        val compKey = key / UUIDTools.generateShortUUID
        val actor = EventsourceProxyActor.start(compKey, ds.ref)
        context.watch(actor)
        eventsourceProxies = eventsourceProxies :+ EventsourceProxyMeta(ds.id, actor, compKey, confirmed = false)
      }
    }

    publishEventsources()
  }

  case class EventsourceProxyMeta(id: String, ref: ActorRef, key: ComponentKey, confirmed: Boolean)


}


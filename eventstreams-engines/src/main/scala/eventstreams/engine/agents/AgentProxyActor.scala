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

package eventstreams.engine.agents

import akka.actor._
import akka.remote.DisassociatedEvent
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.{EventFieldWithValue, WithEventPublisher}
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors._
import eventstreams.core.agent.core.{CommunicationProxyRef, CreateDatasource}
import eventstreams.core.ds.AgentMessagesV1.{AgentDatasourceConfigs, AgentDatasources, AgentInfo}
import eventstreams.core.ds.DatasourceRef
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{OK, Utils}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._

trait AgentProxyActorEvents extends ComponentWithBaseEvents with BaseActorEvents {
  override def componentId: String = "Actor.AgentProxy"

  val InfoUpdate = 'InfoUpdate.info
  val AvailableDatasourcesUpdate = 'AvailableDatasourcesUpdate.info
  val ActiveDatasourcesUpdate = 'ActiveDatasourcesUpdate.info

  val TerminatingDatasourceProxy = 'TerminatingDatasourceProxy.info

  val DatasourceProxyUp = 'DatasourceProxyUp.info

  
}


object AgentProxyActor extends AgentProxyActorEvents {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new AgentProxyActor(key, ref))
}

case class DatasourceProxyAvailable(key: ComponentKey)


class AgentProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with ActorWithDisassociationMonitor
  with SingleComponentActor 
  with AgentProxyActorEvents
  with WithEventPublisher {


  override def commonFields: Seq[EventFieldWithValue] = super.commonFields ++ Seq('Key --> key, 'RemoteActor --> ref)

  private var info: Option[JsValue] = None
  private var dsConfigs: Option[JsValue] = None
  private var datasources = List[DatasourceProxyMeta]()

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! AgentProxyAvailable(key)
    super.preStart()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_LIST => publishDatasources()
    case T_CONFIGTPL => publishConfigs()
  }

  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD =>
      maybeData.foreach(ref ! CreateDatasource(_))
      OK().right
  }

  override def onTerminated(ref: ActorRef): Unit = {
    datasources = datasources.filter(_.ref != ref)
    publishDatasources()

    super.onTerminated(ref)
  }

  private def publishInfo() = T_INFO !! info

  private def publishConfigs() = T_CONFIGTPL !! dsConfigs

  private def publishDatasources() = T_LIST !! Some(Json.toJson(datasources.collect {
    case DatasourceProxyMeta(_, _, k, true) => Json.obj("ckey" -> k.key)
  }))

  private def commonMessageHandler: Receive = {
    case DatasourceProxyAvailable(x) =>
      datasources = datasources.map {
        case b@DatasourceProxyMeta(i, _, k, false) if k == x =>
          DatasourceProxyUp >> ('DatasourceProxyId --> i, 'DatasourceProxyKey --> k)
          b.copy(confirmed = true)
        case v => v
      }
      publishDatasources()

    case AgentInfo(jsValue) => processInfo(jsValue)
    case AgentDatasourceConfigs(jsValue) => processDatasourceConfigs(jsValue)
    case AgentDatasources(list) => processListOfTaps(list)

    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        context.stop(self)
      }

  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    InfoUpdate >> ('Data --> json)
    publishInfo()
  }

  private def processDatasourceConfigs(json: JsValue) = {
    dsConfigs = json #> 'datasourceConfigSchema
    AvailableDatasourcesUpdate >> ('Data --> json)
    publishConfigs()
  }

  private def processListOfTaps(list: List[DatasourceRef]) = {
    ActiveDatasourcesUpdate >> ('List --> list)

    datasources.filterNot { d => list.exists(_.id == d.id)} foreach { dsToKill =>
      TerminatingDatasourceProxy >> ('DatasourceProxyId --> dsToKill.id, 'DatasourceProxyKey --> dsToKill.key, 'Reason --> "No longer active")
      dsToKill.ref ! PoisonPill
    }

    list.foreach { ds =>
      if (!datasources.exists(_.id == ds.id)) {
        val compKey = key / Utils.generateShortUUID
        val actor = DatasourceProxyActor.start(compKey, ds.ref)
        context.watch(actor)
        datasources = datasources :+ DatasourceProxyMeta(ds.id, actor, compKey, confirmed = false)
      }
    }

    publishDatasources()
  }

  case class DatasourceProxyMeta(id: String, ref: ActorRef, key: ComponentKey, confirmed: Boolean)


}


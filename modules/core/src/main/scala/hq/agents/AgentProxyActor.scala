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

import agent.controller.AgentMessagesV1.{AgentDatasources, AgentInfo}
import agent.controller.DatasourceRef
import agent.shared.{CommunicationProxyRef, CreateDatasource}
import akka.actor._
import akka.remote.DisassociatedEvent
import common.{Utils, OK}
import common.actors._
import hq._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._

object AgentProxyActor {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new AgentProxyActor(key, ref))
}

case class DatasourceProxyAvailable(key: ComponentKey)


class AgentProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with ActorWithDisassociationMonitor
  with SingleComponentActor {

  case class DatasourceProxyMeta(id: String, ref: ActorRef, key: ComponentKey, confirmed: Boolean)

  private var info: Option[JsValue] = None

  private var datasources = List[DatasourceProxyMeta]()

  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! AgentProxyAvailable(key)
    logger.debug(s"Agent proxy $key started, pointing at $ref")
    super.preStart()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_LIST => publishDatasources()
  }

  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD =>
      maybeData.foreach(ref ! CreateDatasource(_))
      OK().right
  }

  private def publishInfo() = T_INFO !! info

  private def publishDatasources() = T_LIST !! Some(Json.toJson(datasources.collect{
    case DatasourceProxyMeta(_, _, k, true) => Json.obj("ckey" -> k.key)
  }))

  private def commonMessageHandler: Receive = {
    case DatasourceProxyAvailable(x) =>
      datasources = datasources.map {
        case b @ DatasourceProxyMeta(_, _, k, false) if k == x => b.copy(confirmed = true)
        case v => v
      }
      publishDatasources()

    case AgentInfo(jsValue) => processInfo(jsValue)
    case AgentDatasources(list) => processListOfTaps(list)

    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        context.stop(self)
      }

  }


  override def onTerminated(ref: ActorRef): Unit = {
    datasources = datasources.filter(_.ref != ref)
    publishDatasources()

    super.onTerminated(ref)
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received agent info update: $info")
    publishInfo()
  }

  private def processListOfTaps(list: List[DatasourceRef]) = {
    logger.debug(s"Received datasources update: $list")

    datasources.filterNot { d => list.exists(_.id == d.id) } foreach { dsToKill =>
      logger.debug(s"Datasource $dsToKill is no longer active, terminating proxy")
      dsToKill.ref ! PoisonPill
    }

    list.foreach { ds =>
      if (!datasources.exists(_.id == ds.id)) {
        val compKey = key / Utils.generateShortUUID
        val actor = DatasourceProxyActor.start(compKey, ds.ref)
        context.watch(actor)
        datasources  = datasources :+ DatasourceProxyMeta(ds.id, actor, compKey, confirmed = false)
      }
    }

    publishDatasources()
  }


}


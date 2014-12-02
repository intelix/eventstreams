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

package agent.controller

import java.util.UUID

import agent.controller.AgentMessagesV1.{AgentInfo, AgentDatasources}
import agent.controller.flow.DatasourceActor
import agent.shared._
import akka.actor.{ActorRef, Props, Terminated}
import akka.stream.FlowMaterializer
import com.typesafe.config.Config
import common.Fail
import common.ToolExt.configHelper
import common.actors._
import hq.ComponentKey
import net.ceedubs.ficus.Ficus._
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._

object AgentControllerActor extends ActorObjWithConfig {
  override def id: String = "controller"

  override def props(implicit config: Config) = Props(new AgentControllerActor())
}

case class DatasourceAvailable(key: ComponentKey)

class AgentControllerActor(implicit config: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with ReconnectingActor {

  implicit val mat = FlowMaterializer()
  var commProxy: Option[ActorRef] = None
  var datasources: Map[ComponentKey, ActorRef] = Map()

  override def partialStorageKey: Option[String] = Some("ds/")

  override def commonBehavior: Receive = commonMessageHandler orElse super.commonBehavior

  override def connectionEndpoint: String = config.as[String]("ehub.agent.hq.endpoint")

  override def preStart(): Unit = {
    initiateReconnect()
    super.preStart()
  }

  override def onConnectedToEndpoint(): Unit = {
    remoteActorRef.foreach(_ ! Handshake(self, uuid))
    super.onConnectedToEndpoint()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    commProxy = None
    initiateReconnect()
    super.onDisconnectedFromEndpoint()
  }

  override def afterApplyConfig(): Unit = sendToHQAll()

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = addDatasource(Some(key), Some(props), maybeState)

  private def addDatasource(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      logger.debug(s"Original config for $key: $maybeData ")
      val datasourceKey = key | "ds/" + UUID.randomUUID().toString
      val actor = DatasourceActor.start(datasourceKey)
      context.watch(actor)
      actor ! InitialConfig(data, maybeState)
      (actor, datasourceKey)
    }

  private def commonMessageHandler: Receive = {
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case CreateDatasource(cfg) => addDatasource(None, Some(cfg), None) match {
      case \/-((actor, name)) =>
        logger.info(s"Datasource $name successfully created")
      case -\/(error) =>
        logger.error(s"Unable to create datasource: $error")
    }
    case Terminated(ref) =>
      datasources = datasources.filter {
        case (route, otherRef) => otherRef != ref
      }
      sendToHQ(snapshot)

    case DatasourceAvailable(key) =>
      logger.debug(s"Available datasource: $key")
      datasources = datasources + (key -> sender())
      sendToHQ(snapshot)
  }

  private def sendToHQAll() = {
    sendToHQ(info)
    sendToHQ(snapshot)
  }

  private def sendToHQ(msg: Any) =
    commProxy foreach { actor =>
      logger.debug(s"$msg -> $actor")
      actor ! msg
    }

  private def snapshot = AgentDatasources(
    datasources.map {
      case (key, ref) => DatasourceRef(key.key, ref)
    }.toList
  )

  private def info = AgentInfo(Json.obj(
    "name" -> config.as[String]("ehub.agent.name"),
    "description" -> config.as[String]("ehub.agent.description"),
    "location" -> config.as[String]("ehub.agent.location"),
    "address" -> context.self.path.address.toString,
    "state" -> "active"
  ))


}
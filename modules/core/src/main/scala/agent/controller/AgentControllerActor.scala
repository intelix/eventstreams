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

import agent.controller.flow.{InitialiseWith, DatasourceConfigUpdate, DatasourceActor, DatasourceStateUpdate}
import agent.controller.storage._
import agent.shared._
import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import com.typesafe.config.Config
import common.actors._
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable

object AgentControllerActor extends ActorObjWithConfig {
  override def id: String = "controller"

  override def props(implicit config: Config) = Props(new AgentControllerActor())
}

class AgentControllerActor(implicit config: Config)
  extends ActorWithComposableBehavior
  with ReconnectingActor {

  implicit val system = context.system
  implicit val mat = FlowMaterializer()

  val tapActors: mutable.Map[Long, ActorRef] = mutable.HashMap()
  var storage = ConfigStorageActor.path
  var commProxy: Option[ActorRef] = None

  override def commonBehavior: Receive = commonMessageHandler orElse super.commonBehavior

  override def connectionEndpoint: String = config.as[String]("agent.hq.endpoint")

  override def preStart(): Unit = {
    initiateReconnect()
    switchToCustomBehavior(handleInitialisationMessages, Some("awaiting initialisation"))
    storage ! RetrieveConfigForAll()
    super.preStart()
  }

  def createActor(tapId: Long, config: String, maybeState: Option[String]): Unit = {
    val actorId = actorFriendlyId(tapId.toString)
    tapActors.get(tapId).foreach { actor =>
      logger.info(s"Stopping $actor")
      context.stop(actor)
    }
    logger.info(s"Creating a new actor for tap $tapId")
    val actor = context.actorOf(DatasourceActor.props(tapId, Json.parse(config), maybeState.map(Json.parse)), actorId)
    actor ! InitialiseWith(Json.parse(config), maybeState.map(Json.parse))
    tapActors += (tapId -> actor)
    sendToHQ(snapshot)
  }


  override def onConnectedToEndpoint(): Unit = {
    remoteActorRef.foreach(_ ! Handshake(self, config.as[String]("agent.name")))
    super.onConnectedToEndpoint()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    commProxy = None
    initiateReconnect()
    super.onDisconnectedFromEndpoint()
  }

  private def commonMessageHandler: Receive = {
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
  }

  private def nextAvailableId: Long = {
    if (tapActors.keys.isEmpty) {
      0
    } else tapActors.keys.max + 1
  }

  private def sendToHQAll() = {
    sendToHQ(info)
    sendToHQ(snapshot)
  }

  private def sendToHQ(json: JsValue) = {
    commProxy foreach { actor =>
      logger.debug(s"$json -> $actor")
      actor ! GenericJSONMessage(json.toString())
    }
  }

  private def snapshot = Json.obj(
    "taps" -> Json.toJson(
      tapActors.keys.toArray.sorted.map { key => Json.obj(
        "id" -> actorFriendlyId(key.toString),
        "ref" -> ("controller/" + actorFriendlyId(key.toString))
      )
      }
    )
  )

  private def info = Json.obj(
    "info" -> Json.obj(
      "name" -> config.as[String]("agent.name"),
      "description" -> config.as[String]("agent.description"),
      "location" -> config.as[String]("agent.location"),
      "address" -> context.self.path.address.toString,
      "state" -> "active"
    )
  )

  private def handleInitialisationMessages: Receive = {
    case StoredConfigs(list) =>
      logger.debug(s"Received list of tap from the storage: $list")
      list.foreach {
        case StoredConfig(id, Some(TapInstance(_, cfg, state))) =>
          createActor(id, cfg, state)
        case StoredConfig(id, None) =>
          logger.warn(s"No config defined for tap ID $id")
      }
      sendToHQAll()
      switchToCustomBehavior(handleTapOpMessages, Some("existing taps loaded"))
  }

  private def handleTapOpMessages: Receive = {

    case CreateTap(cfg) =>
      logger.info("Creating tap with config " + cfg)

      val c = (cfg \ "config").as[JsValue]

      val tapId = (c \ "tapId").asOpt[Long] getOrElse nextAvailableId
      val cfgAsStr: String = Json.stringify(c)
      logger.info("Creating tap " + tapId)
      storage ! StoreDatasourceInstance(TapInstance(tapId, cfgAsStr, None))
      createActor(tapId, cfgAsStr, None)
    case DatasourceStateUpdate(id, state) =>
      logger.info(s"Tap state update: id=$id state=$state")
      storage ! StoreDatasourceState(TapState(id, Some(Json.stringify(state))))
    case DatasourceConfigUpdate(id, newConfig, newState) =>
      logger.info(s"Tap config update: id=$id config=$newConfig state=$newState")
      storage ! StoreDatasourceConfig(TapConfig(id, Json.stringify(newConfig)))
  }


}
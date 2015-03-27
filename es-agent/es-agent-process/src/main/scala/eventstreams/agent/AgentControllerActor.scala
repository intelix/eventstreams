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

import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Model
import eventstreams.agent.AgentMessagesV1.{AgentEventsourceConfigs, AgentEventsources, AgentInfo}
import eventstreams.core.actors._
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.extensions._
import play.api.libs.json.{JsValue, Json, _}

import scala.concurrent.duration.FiniteDuration
import scala.io
import scalaz.Scalaz._
import scalaz._

trait AgentControllerSysevents
  extends ComponentWithBaseSysevents
  with GenericModelManagerSysevents
  with BaseActorSysevents
  with ReconnectingActorSysevents {

  val AvailableEventsources = 'AvailableEventsources.info
  val AgentInstanceAvailable = 'AgentInstanceAvailable.info
  val EventsourceInstanceCreated = 'EventsourceInstanceCreated.info
  val MessageToAgentProxy = 'MessageToAgentProxy.trace

  override def componentId: String = "Agent.Controller"
}

object AgentControllerActor extends ActorObjWithConfig with AgentControllerSysevents {
  override def id: String = "controller"

  override def props(implicit config: Config) = Props(new AgentControllerActor())
}

case class EventsourceAvailable(id: String, ref: ActorRef, name: String) extends Model

class AgentControllerActor(implicit sysconfig: Config)
  extends ActorWithComposableBehavior
  with GenericModelManager[EventsourceAvailable]
  with ReconnectingActor
  with AgentControllerSysevents
  with WithSyseventPublisher {

  override def entityId: String = "esources"

  lazy val eventsourcesConfigsList = {
    val list = sysconfig.getConfigList("eventstreams.eventsources.sources")
    (0 until list.size()).map(list.get).toList.sortBy[String](_.getString("name"))
  }
  lazy val configSchema = {
    var mainConfigSchema = Json.parse(
      io.Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("eventstreams.eventsources.main-schema"))).mkString)
    var oneOf = (mainConfigSchema \ "properties" \ "source" \ "oneOf").asOpt[JsArray].map(_.value) | Array[JsValue]()

    val eventsourceSchemas = eventsourcesConfigsList.map { cfg =>
      val schemaResourceName = cfg.getString("config.schema")
      val resource = getClass.getResourceAsStream(schemaResourceName)
      val schemaContents = io.Source.fromInputStream(resource).mkString
      Json.parse(schemaContents)
    }

    var counter = 0
    eventsourceSchemas.foreach { instruction =>
      counter = counter + 1
      val refName = "ref" + counter
      oneOf = oneOf :+ Json.obj("$ref" -> s"#/definitions/$refName")
      val defPath = __ \ "definitions" \ refName
      mainConfigSchema = mainConfigSchema.set(
        defPath -> instruction
      )
    }

    mainConfigSchema.set(
      __ \ "properties" \ "source" \ "oneOf" -> Json.toJson(oneOf.toArray)
    )
  }


  implicit val mat = FlowMaterializer()
  var commProxy: Option[ActorRef] = None

  override def commonBehavior: Receive = commonMessageHandler orElse super.commonBehavior

  override def connectionEndpoint: Option[String] = sysconfig.as[Option[String]]("eventstreams.agent.hq.endpoint")
  override def reconnectAttemptInterval: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.agent.hub-reconnect-attempt-interval") | super.reconnectAttemptInterval
  override def remoteAssociationTimeout: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.agent.hub-handshake-timeout") | super.remoteAssociationTimeout

  
  override def preStart(): Unit = {
    initiateReconnect()
    super.preStart()

    val list = eventsourcesConfigsList.map { next =>
      next.getString("name") + "@" + next.getString("class")
    }.mkString(",")

    AgentInstanceAvailable >> ('Id -> uuid)
    AvailableEventsources >> ('List -> list)
    
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

  private def commonMessageHandler: Receive = {
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case CreateEventsource(cfg) => createModelInstance(None, Some(Json.parse(cfg)), None, None) match {
      case \/-((nm, actor, _)) =>
        EventsourceInstanceCreated >> ('Name -> nm, 'Actor -> actor, 'Config -> cfg)
      case -\/(error) =>
        Error >> ('Message -> "Unable to create eventsource instance", 'Error -> error, 'Config -> cfg)
    }
  }

  private def sendToHQAll() = {
    sendToHQ(dsConfigs)
    sendToHQ(info)
    sendToHQ(snapshot)
  }

  private def sendToHQ(msg: Any) =
    commProxy foreach { actor =>
      MessageToAgentProxy >> ('Message -> msg)
      actor ! msg
    }

  private def snapshot = AgentEventsources(
    entries.map {
      case e => EventsourceRef(e.id, e.ref)
    }.toList
  )

  private def info = AgentInfo(Json.obj(
    "name" -> sysconfig.as[String]("eventstreams.agent.name"),
    "description" -> sysconfig.as[String]("eventstreams.agent.description"),
    "location" -> sysconfig.as[String]("eventstreams.agent.location"),
    "address" -> context.self.path.address.toString,
    "state" -> "active",
    "eventsourceConfigSchema" -> configSchema
  ))

  private def dsConfigs = AgentEventsourceConfigs(Json.obj(
    "eventsourceConfigSchema" -> configSchema
  ))


  override def onModelListChange(): Unit = {
    sendToHQAll()
    super.onModelListChange()
  }

  override def startModelActor(entityId: String, config: ModelConfigSnapshot): ActorRef = EventsourceActor.start(entityId, config, eventsourcesConfigsList)

}
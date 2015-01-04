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

package eventstreams.agent

import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import com.typesafe.config.Config
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.agent.flow.DatasourceActor
import eventstreams.core.actors._
import eventstreams.core.agent.core.{CommunicationProxyRef, CreateDatasource, Handshake}
import eventstreams.core.ds.AgentMessagesV1.{AgentDatasourceConfigs, AgentDatasources, AgentInfo}
import eventstreams.core.ds.DatasourceRef
import eventstreams.core.messages.ComponentKey
import eventstreams.core.{Fail, NowProvider, Utils}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.extensions._
import play.api.libs.json.{JsValue, Json, _}

import scala.concurrent.duration.FiniteDuration
import scala.io
import scalaz.Scalaz._
import scalaz._

trait AgentControllerEvents 
  extends ComponentWithBaseEvents 
  with BaseActorEvents 
  with ReconnectingActorEvents {

  val AvailableDatasources = 'AvailableDatasources.info
  val AgentInstanceAvailable = 'AgentInstanceAvailable.info
  val DatasourceInstanceAvailable = 'DatasourceInstanceAvailable.info
  val DatasourceInstanceCreated = 'DatasourceInstanceCreated.info
  val MessageToAgentProxy = 'MessageToAgentProxy.trace

  override def componentId: String = "Actor.AgentController"
}

object AgentControllerActor extends ActorObjWithConfig with AgentControllerEvents {
  override def id: String = "controller"

  override def props(implicit config: Config) = Props(new AgentControllerActor())
}

case class DatasourceAvailable(key: ComponentKey)

class AgentControllerActor(implicit sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with ReconnectingActor
  with NowProvider
  with AgentControllerEvents with WithEventPublisher {


  lazy val datasourcesConfigsList = {
    val list = sysconfig.getConfigList("ehub.datasources.sources")
    (0 until list.size()).map(list.get).toList.sortBy[String](_.getString("name"))
  }
  lazy val configSchema = {
    var mainConfigSchema = Json.parse(
      io.Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("ehub.datasources.main-schema"))).mkString)
    var oneOf = (mainConfigSchema \ "properties" \ "source" \ "oneOf").asOpt[JsArray].map(_.value) | Array[JsValue]()

    val datasourceSchemas = datasourcesConfigsList.map { cfg =>
      val schemaResourceName = cfg.getString("config.schema")
      val resource = getClass.getResourceAsStream(schemaResourceName)
      val schemaContents = io.Source.fromInputStream(resource).mkString
      Json.parse(schemaContents)
    }

    var counter = 0
    datasourceSchemas.foreach { instruction =>
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
  var datasources: Map[ComponentKey, ActorRef] = Map()

  override def partialStorageKey: Option[String] = Some("ds/")

  override def commonBehavior: Receive = commonMessageHandler orElse super.commonBehavior

  override def connectionEndpoint: String = sysconfig.as[String]("ehub.agent.hq.endpoint")


  override def reconnectAttemptInterval: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("ehub.agent.hq.reconnectAttemptInterval") | super.reconnectAttemptInterval
  override def remoteAssociationTimeout: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("ehub.agent.hq.remoteAssociationTimeout") | super.remoteAssociationTimeout

  
  override def preStart(): Unit = {
    initiateReconnect()
    super.preStart()

    val list = datasourcesConfigsList.map { next =>
      next.getString("name") + "@" + next.getString("class")
    }.mkString(",")

    AgentInstanceAvailable >> ('Id --> uuid)
    AvailableDatasources >> ('List --> list)
    
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
      val datasourceKey = key | "ds/" + Utils.generateShortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = DatasourceActor.start(datasourceKey, datasourcesConfigsList)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      (actor, datasourceKey)
    }


  override def onTerminated(ref: ActorRef): Unit = {
    datasources = datasources.filter {
      case (route, otherRef) => otherRef != ref
    }
    sendToHQ(snapshot)

    super.onTerminated(ref)
  }

  private def commonMessageHandler: Receive = {
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case CreateDatasource(cfg) => addDatasource(None, Some(cfg), None) match {
      case \/-((actor, name)) =>
        DatasourceInstanceCreated >> ('Name --> name, 'Actor --> actor, 'Config --> cfg)
      case -\/(error) =>
        Error >> ('Message --> "Unable to create datasource instance", 'Error --> error, 'Config --> cfg)
    }
    case DatasourceAvailable(key) =>
      DatasourceInstanceAvailable >> ('Key --> key, 'Actor --> sender())
      datasources = datasources + (key -> sender())
      sendToHQ(snapshot)
  }

  private def sendToHQAll() = {
    sendToHQ(dsConfigs)
    sendToHQ(info)
    sendToHQ(snapshot)
  }

  private def sendToHQ(msg: Any) =
    commProxy foreach { actor =>
      MessageToAgentProxy >> ('Message --> msg)
      actor ! msg
    }

  private def snapshot = AgentDatasources(
    datasources.map {
      case (key, ref) => DatasourceRef(key.key, ref)
    }.toList
  )

  private def info = AgentInfo(Json.obj(
    "name" -> sysconfig.as[String]("ehub.agent.name"),
    "description" -> sysconfig.as[String]("ehub.agent.description"),
    "location" -> sysconfig.as[String]("ehub.agent.location"),
    "address" -> context.self.path.address.toString,
    "state" -> "active",
    "datasourceConfigSchema" -> configSchema
  ))

  private def dsConfigs = AgentDatasourceConfigs(Json.obj(
    "datasourceConfigSchema" -> configSchema
  ))


}
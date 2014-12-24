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

package eventstreams.engine.flows

import akka.actor._
import com.typesafe.config.Config
import eventstreams.core.actors._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{Fail, OK, Utils}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.io
import scalaz.Scalaz._
import scalaz._


object FlowManagerActor extends ActorObjWithConfig {
  def id = "flows"

  def props(implicit config: Config) = Props(new FlowManagerActor(config))
}

case class FlowAvailable(id: ComponentKey)

class FlowManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with SingleComponentActor {

  val instructionsConfigsList = {
    val list = sysconfig.getConfigList("ehub.flows.instructions")
    (0 until list.size()).map(list.get).toList.sortBy[String](_.getString("name"))
  }

  val configSchema = {
    var mainConfigSchema = Json.parse(
      io.Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("ehub.flows.main-schema"))).mkString)
    var oneOf = (mainConfigSchema \ "properties" \ "pipeline" \ "items" \ "oneOf").asOpt[JsArray].map(_.value) | Array[JsValue]()

    val instructionSchemas = instructionsConfigsList.map { cfg =>
      val schemaResourceName = cfg.getString("config.schema")
      val resource = getClass.getResourceAsStream(schemaResourceName)
      val schemaContents = io.Source.fromInputStream(resource).mkString
      Json.parse(schemaContents)
    }

    var counter = 0
    instructionSchemas.foreach { instruction =>
      counter = counter + 1
      val refName = "ref" + counter
      oneOf = oneOf :+ Json.obj("$ref" -> s"#/definitions/$refName")
      val defPath = __ \ "definitions" \ refName
      mainConfigSchema = mainConfigSchema.set(
        defPath -> instruction
      )
    }

    mainConfigSchema.set(
      __ \ "properties" \ "pipeline" \ "items" \ "oneOf" -> Json.toJson(oneOf.toArray)
    )
  }

  type FlowMap = Map[ComponentKey, ActorRef]
  override val key = ComponentKey("flows")
  var flows: Monitored[FlowMap] = withMonitor[FlowMap](listUpdate)(Map())

  def publishConfigTpl(): Unit = T_CONFIGTPL !! Some(configSchema)

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def partialStorageKey = Some("flow/")

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = startActor(Some(key), Some(props), maybeState)

  def listUpdate = topicUpdateEffect(T_LIST, list)

  def list = () => Some(Json.toJson(flows.get.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => listUpdate()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => startActor(None, maybeData, None)
  }


  override def onTerminated(ref: ActorRef): Unit = {
    flows = flows.map { list =>
      list.filter {
        case (route, otherRef) => otherRef != ref
      }
    }

    super.onTerminated(ref)
  }

  def handler: Receive = {
    case FlowAvailable(route) =>
      flows = flows.map { list => list + (route -> sender())}
  }

  private def startActor(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]): \/[Fail, OK] =
    for (
      data <- maybeData \/> Fail("Invalid payload", Some("Invalid configuration"))
    ) yield {
      val flowKey = key | "flow/" + Utils.generateShortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = FlowActor.start(flowKey, instructionsConfigsList)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("Flow successfully created")
    }


}

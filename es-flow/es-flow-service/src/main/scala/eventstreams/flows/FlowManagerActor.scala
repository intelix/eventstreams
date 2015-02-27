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

package eventstreams.flows

import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.io.Source
import scalaz.Scalaz._


trait FlowManagerActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with RouteeSysevents {
  override def componentId: String = "Flow.Manager"

  val FlowBecomeAvailable = 'FlowBecomeAvailable.info

}

object FlowManagerActor extends ActorObj with FlowManagerActorSysevents {
  def id = "flows"

  def props(config: Config, cluster: Cluster) = Props(new FlowManagerActor(config, cluster))
}


case class FlowAvailable(id: ComponentKey, ref: ActorRef, name: String) extends Model

class FlowManagerActor(sysconfig: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeModelManager[FlowAvailable]
  with NowProvider
  with FlowManagerActorSysevents
  with WithSyseventPublisher {

  val id = FlowManagerActor.id

  val instructionsConfigsList = {
    val list = sysconfig.getConfigList("eventstreams.instructions")
    (0 until list.size()).map(list.get).toList.sortBy[String](_.getString("name"))
  }

  override val configSchema = Some({
    var mainConfigSchema = Json.parse(
      Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("eventstreams.flows.main-schema"))).mkString)
    var oneOf = (mainConfigSchema \ "properties" \ "pipeline" \ "items" \ "oneOf").asOpt[JsArray].map(_.value) | Array[JsValue]()

    val instructionSchemas = instructionsConfigsList.map { cfg =>
      val schemaResourceName = cfg.getString("config.schema")
      val resource = getClass.getResourceAsStream(schemaResourceName)
      val schemaContents = Source.fromInputStream(resource).mkString
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
  })

  override val key = ComponentKey(id)

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def list = Some(Json.toJson(entries.map { x => Json.obj("ckey" -> x.id.key)}.toArray))


  def handler: Receive = {
    case e: FlowAvailable => addEntry(e)
  }

  override def startModelActor(key: String): ActorRef = FlowProxyActor.start(key, instructionsConfigsList)
}

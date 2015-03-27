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

package eventstreams.gates

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._

import scala.io.Source

trait GateManagerSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "Gate.GateManager"
}

object GateManagerActor extends GateManagerSysevents {
  val id = "gates"
  def props(config: Config, cluster: Cluster) = Props(new GateManagerActor(config, cluster))
}


case class GateAvailable(id: String, ref: ActorRef, name: String) extends Model

class GateManagerActor(sysconfig: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithConfigAutoLoad
  with RouteeModelManager[GateAvailable]
  with ActorWithInstrumentationEnabled
  with GateManagerSysevents
  with WithSyseventPublisher {


  override val configSchema = Some(Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.gates.gate-schema"))).mkString))


  override def entityId: String = GateManagerActor.id

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def list = Some(Json.toJson(entries.map { x => Json.obj("ckey" -> x.id)}.toArray))

  def handler: Receive = {
    case x: GateAvailable => addEntry(x)
  }

  override def startModelActor(routeeKey: String, config: ModelConfigSnapshot): ActorRef = GateActor.start(routeeKey, config)

  override def sensorComponentSubId: Option[String] = None
}

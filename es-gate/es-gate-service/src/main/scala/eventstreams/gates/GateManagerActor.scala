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
import play.api.libs.json.extensions._

import scala.io.Source
import scalaz.Scalaz._

trait GateManagerSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Gate.GateManager"
}

object GateManagerActor extends  GateManagerSysevents {
  val id = "gates"
}

case class GateAvailable(id: ComponentKey)

class GateManagerActor(sysconfig: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider
  with GateManagerSysevents
  with WithSyseventPublisher {

  val id = GateManagerActor.id

  val configSchema = Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.gates.gate-schema"))).mkString)

  type GatesMap = Map[ComponentKey, ActorRef]

  override val key = ComponentKey(id)
  var gates: GatesMap = Map()

  override def partialStorageKey: Option[String] = Some("gate/")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def listUpdate() = T_LIST !! list

  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))


  def publishConfigTpl(): Unit = T_CONFIGTPL !! Some(configSchema)

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_LIST => listUpdate()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_ADD => addGate(None, maybeData, None)
  }


  override def onTerminated(ref: ActorRef): Unit = {
    gates = gates.filter {
      case (route, otherRef) => otherRef != ref
    }
    listUpdate()
    super.onTerminated(ref)
  }

  def handler: Receive = {
    case GateAvailable(route) =>
      gates = gates + (route -> sender())
      listUpdate()

  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = addGate(Some(key), Some(props), maybeState)

  private def addGate(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      val gateKey = key | "gate/" + shortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = GateActor.start(gateKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("Gate successfully created")
    }


}

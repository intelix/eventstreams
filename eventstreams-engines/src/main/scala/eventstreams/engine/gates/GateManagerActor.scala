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

package eventstreams.engine.gates

import akka.actor._
import com.typesafe.config.Config
import eventstreams.core.actors._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{Fail, NowProvider, OK}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.io.Source
import scalaz.Scalaz._


object GateManagerActor extends ActorObjWithConfig {
  def id = "gates"

  def props(implicit config: Config) = Props(new GateManagerActor(config))
}

case class GateAvailable(id: ComponentKey)

class GateManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with SingleComponentActor
  with NowProvider {

  val configSchema = Json.parse(
      Source.fromInputStream(
        getClass.getResourceAsStream(
          sysconfig.getString("ehub.gates.main-schema"))).mkString)

  type GatesMap = Map[ComponentKey, ActorRef]

  override val key = ComponentKey("gates")
  var gates: Monitored[GatesMap] = withMonitor[GatesMap](listUpdate)(Map())

  override def partialStorageKey: Option[String] = Some("gate/")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def listUpdate = topicUpdateEffect(T_LIST, list)

  def list = () => Some(Json.toJson(gates.get.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))


  def publishConfigTpl(): Unit = T_CONFIGTPL !! Some(configSchema)

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => listUpdate()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => addGate(None, maybeData, None)

  }


  override def onTerminated(ref: ActorRef): Unit = {
    gates = gates.map { list =>
      list.filter {
        case (route, otherRef) => otherRef != ref
      }
    }
    super.onTerminated(ref)
  }

  def handler: Receive = {
    case GateAvailable(route) => gates = gates.map { list => list + (route -> sender())}

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

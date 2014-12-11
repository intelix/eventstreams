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

package hq.gates

import java.util.UUID

import akka.actor._
import common.actors._
import common.{Utils, NowProvider, Fail, OK}
import hq._
import hq.flows.FlowActor
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.mutable
import scalaz.Scalaz._
import scalaz.\/


object GateSensorManagerActor {
  def props(id: String) = Props(new GateSensorManagerActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}

case class GateSensorAvailable(id: ComponentKey)

class GateSensorManagerActor(id: String)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with SingleComponentActor
  with NowProvider {

  override val key = ComponentKey(id + "/sensors")

  var sensors = mutable.Map[ComponentKey, ActorRef]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def partialStorageKey = Some(id + "/sensor")

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = startActor(Some(key), Some(props), maybeState)

  def publishList() = T_LIST !! list

  def list = Some(Json.toJson(sensors.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => publishList()
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => startActor(None, maybeData, None)
  }

  def handler: Receive = {
    case GateSensorAvailable(route) =>
      sensors += route -> sender()
    case Terminated(ref) =>
      sensors.collect { case (k, v) if v == ref => k} foreach sensors.remove
  }

  private def startActor(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]): \/[Fail, OK] =
    for (
      data <- maybeData \/> Fail("Invalid payload", Some("Invalid configuration"))
    ) yield {
      val entryKey = key | id + "/sensor/" + shortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = GateSensorActor.start(entryKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("Sensor successfully created")
    }

}

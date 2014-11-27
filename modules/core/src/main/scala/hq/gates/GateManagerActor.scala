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
import common.{OK, Fail}
import common.ToolExt.configHelper
import common.actors._
import common.storage._
import hq._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._


object GateManagerActor extends ActorObjWithoutConfig {
  def id = "gates"

  def props = Props(new GateManagerActor())
}

case class GateAvailable(id: ComponentKey)

class GateManagerActor
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with SingleComponentActor {

  override val key = ComponentKey("gates")
  var gates: Map[ComponentKey, ActorRef] = Map()

  override def partialStorageKey: Option[String] = Some("gate/")

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => topicUpdate(topic, list, singleTarget = Some(ref))
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => addGate(None, maybeData, None)

  }

  def handler: Receive = {
    case GateAvailable(route) =>
      gates = gates + (route -> sender())
      topicUpdate(TopicKey("list"), list)
    case Terminated(ref) =>
      gates = gates.filter {
        case (route, otherRef) => otherRef != ref
      }
      topicUpdate(TopicKey("list"), list)
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = addGate(Some(key), Some(props), maybeState)

  private def addGate(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      val gateKey = key | "gate/" + UUID.randomUUID().toString
      val actor = GateActor.start(gateKey)
      context.watch(actor)
      actor ! InitialConfig(data, maybeState)
      OK("Gate successfully created")
    }

}

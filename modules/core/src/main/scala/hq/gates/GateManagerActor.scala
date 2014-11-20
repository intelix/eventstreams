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

import akka.actor._
import common.actors.{ActorObjWithoutConfig, ActorWithComposableBehavior, SingleComponentActor}
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
  with SingleComponentActor {

  override val key = ComponentKey("gates")

  val configStore = ConfigStorageActor.path

  var gates: Map[ComponentKey, ActorRef] = Map()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  override def preStart(): Unit = {
    configStore ! RetrieveConfigForAllMatching("gate/")
    super.preStart()
  }

  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => topicUpdate(topic, list, singleTarget = Some(ref))
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => addGate(maybeData) match {
      case \/-((actor, name)) =>
        genericCommandSuccess(T_ADD, replyToSubj, Some(s"Gate $name successfully created"))
        configStore ! StoreSnapshot(EntryConfigSnapshot("gate/" + name, Json.stringify(maybeData.get), None))
      case -\/(error) => genericCommandError(T_ADD, replyToSubj, s"Unable to create gate: $error")
    }

  }

  def handler: Receive = {
    case StoredConfigs(list) =>
      list foreach { c => addGate(c.config.map { e => Json.parse(e.config)})}
    case GateAvailable(route) =>
      gates = gates + (route -> sender())
      topicUpdate(TopicKey("list"), list)
    case Terminated(ref) =>
      gates = gates.filter {
        case (route, otherRef) => otherRef != ref
      }
      topicUpdate(TopicKey("list"), list)
    // TODO remove config entry

  }

  private def addGate(maybeData: Option[JsValue]) =
    for (
      data <- maybeData.toRightDisjunction("Invalid payload");
      name <- (data \ "name").asOpt[String].toRightDisjunction("No name provided");
      nonEmptyName <- if (name.isEmpty) -\/("Name is blank") else name.right
    ) yield {
      val actor = GateActor.start(nonEmptyName)
      context.watch(actor)
      (actor, nonEmptyName)
    }


}

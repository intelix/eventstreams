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

import akka.actor.Status.Success
import akka.actor._
import common.actors.{ActorObjWithoutConfig, ActorWithComposableBehavior, SingleComponentActor}
import hq._
import play.api.libs.json.{JsValue, Json}
import scalaz._
import Scalaz._

import scala.util.Try


object GateManagerActor extends ActorObjWithoutConfig {
  def id = "gates"

  def props = Props(new GateManagerActor())
}

case class GateAvailable(id: ComponentKey)

class GateManagerActor
  extends ActorWithComposableBehavior
  with SingleComponentActor {

  override val key = ComponentKey("gates")

  var gates: Map[ComponentKey, ActorRef] = Map()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  def list = Some(Json.toJson(gates.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => topicUpdate(topic, list, singleTarget = Some(ref))
  }


  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => {
      for (
        data <- maybeData.toRightDisjunction("Invalid payload");
        name <- (data \ "name").asOpt[String].toRightDisjunction("No name provided");
        nonEmptyName <- if (name.isEmpty) -\/("Name is blank") else name.right
      ) yield (GateActor.start(nonEmptyName), nonEmptyName)
    } match {
      case \/-((actor,name)) =>
        context.watch(actor)
        genericCommandSuccess(T_ADD, replyToSubj, Some(s"Gate $name successfully created"))
      case -\/(error) => genericCommandError(T_ADD, replyToSubj, s"Unable to create gate: $error")
    }
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


}

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

package hq.flows

import akka.actor._
import common.{OK, Fail}
import common.ToolExt.configHelper
import common.actors._
import hq._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._


object FlowManagerActor extends ActorObjWithoutConfig {
  def id = "flows"

  def props = Props(new FlowManagerActor())
}

case class FlowAvailable(id: ComponentKey)

class FlowManagerActor
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with SingleComponentActor {

  override val key = ComponentKey("flows")

  var flows: Map[ComponentKey, ActorRef] = Map()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def partialStorageKey = Some("flow/")

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = startActor(Some(props), maybeState)

  def list = Some(Json.toJson(flows.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => topicUpdate(topic, list, singleTarget = Some(ref))
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => startActor(maybeData, None)
  }

  def handler: Receive = {
    case FlowAvailable(route) =>
      flows = flows + (route -> sender())
      topicUpdate(TopicKey("list"), list)
    case Terminated(ref) =>
      flows = flows.filter {
        case (route, otherRef) => otherRef != ref
      }
      topicUpdate(TopicKey("list"), list)
  }

  private def startActor(maybeData: Option[JsValue], maybeState: Option[JsValue]) : \/[Fail,OK] =
    for (
      data <- maybeData \/> Fail("Invalid payload", Some("Invalid configuration"));
      name <- data ~> 'name \/> Fail("No name provided", Some("Invalid configuration"));
      nonEmptyName <- if (name.isEmpty) -\/(Fail("Name is blank", Some("Invalid configuration"))) else name.right;
      config <- data #> 'config \/> Fail("Empty config", Some("Invalid configuration"))
    ) yield {
      val actor = FlowActor.start(nonEmptyName)
      context.watch(actor)
      actor ! InitialConfig(data, maybeState)
      OK("Flow successfully added")
    }


}

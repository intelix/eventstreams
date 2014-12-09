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

import java.util.UUID

import akka.actor._
import common.{OK, Fail}
import common.ToolExt.configHelper
import common.actors._
import hq._
import play.api.libs.json._
import play.api.libs.json.extensions._

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

  type FlowMap = Map[ComponentKey, ActorRef]

  var flows: Monitored[FlowMap] = withMonitor[FlowMap](listUpdate)(Map())

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def partialStorageKey = Some("flow/")

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = startActor(Some(key), Some(props), maybeState)

  def listUpdate = topicUpdateEffect(T_LIST, list)
  def list = () => Some(Json.toJson(flows.get.keys.map { x => Json.obj("id" -> x.key)}.toArray))


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => listUpdate()
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => startActor(None, maybeData, None)
  }

  def handler: Receive = {
    case FlowAvailable(route) =>
      flows = flows.map { list => list + (route -> sender())}
    case Terminated(ref) =>
      flows = flows.map { list =>
        list.filter {
          case (route, otherRef) => otherRef != ref
        }
      }
  }

  private def startActor(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) : \/[Fail,OK] =
    for (
      data <- maybeData \/> Fail("Invalid payload", Some("Invalid configuration"))
    ) yield {
      val flowKey = key | "flow/" + UUID.randomUUID().toString
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = FlowActor.start(flowKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("Flow successfully created")
    }


}

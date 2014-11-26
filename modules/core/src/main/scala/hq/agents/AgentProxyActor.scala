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

package hq.agents

import agent.shared.{CommunicationProxyRef, CreateTap, GenericJSONMessage}
import akka.actor._
import akka.remote.DisassociatedEvent
import common.OK
import common.actors._
import hq._
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import scalaz._
import Scalaz._

object AgentProxyActor {
  def start(key: ComponentKey, ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(key, ref), key.toActorId)

  def props(key: ComponentKey, ref: ActorRef) = Props(new AgentProxyActor(key, ref))
}

case class TapAvailable(id: ComponentKey)


class AgentProxyActor(val key: ComponentKey, ref: ActorRef)
  extends PipelineWithStatesActor
  with SingleComponentActor {

  val T_TAPS = TopicKey("taps")
  val T_ADD_TAP = TopicKey("addTap")

  private var info: Option[JsValue] = None

  private var activeTaps: Map[String, ActorRef] = Map()
  private var confirmedTaps: List[ComponentKey] = List()


  override def commonBehavior: Actor.Receive = commonMessageHandler orElse super.commonBehavior

  override def preStart(): Unit = {
    ref ! CommunicationProxyRef(self)
    context.parent ! AgentAvailable(key)
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    context.system.eventStream.unsubscribe(self, classOf[DisassociatedEvent])
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
    case T_TAPS => topicUpdate(T_TAPS, taps, singleTarget = Some(ref))
  }

  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD_TAP =>
      maybeData.foreach(ref ! CreateTap(_))
      OK().right
  }

  private def commonMessageHandler: Receive = {
    case TapAvailable(x) =>
      confirmedTaps = confirmedTaps :+ x
      topicUpdate(T_TAPS, taps)
    case GenericJSONMessage(jsonString) =>
      Json.parse(jsonString).asOpt[JsValue] foreach process
    case DisassociatedEvent(_, remoteAddr, _) =>
      if (ref.path.address == remoteAddr) {
        self ! PoisonPill
      }
    case StartTapProxy(id, tapRef) =>
      if (!activeTaps.contains(id)) activeTaps = activeTaps + (id -> DatasourceProxyActor.start(key / id, tapRef))

  }

  private def taps = Some(Json.toJson(confirmedTaps.map { x => Json.obj("id" -> x.key)}))

  private def process(json: JsValue) = {
    (json \ "info").asOpt[JsValue] foreach processInfo
    (json \ "taps").asOpt[JsArray] foreach processListOfTaps
  }

  private def processInfo(json: JsValue) = {
    info = Some(json)
    logger.debug(s"Received agent info update: $info")
    topicUpdate(T_INFO, info)
  }

  private def processListOfTaps(json: JsArray) = {
    logger.debug(s"Received taps update: $json")

    val listOfIds = for (
      tapDetails <- json.value;
      id <- (tapDetails \ "id").asOpt[String];
      ref <- (tapDetails \ "ref").asOpt[String]
    ) yield id -> ref

    activeTaps.keys filterNot listOfIds.contains foreach killTapProxy
    listOfIds.filterNot {
      case (i, r) => activeTaps.contains(i)
    } foreach {
      case (i, r) => createTapProxy(i, r)
    }
  }

  private def createTapProxy(id: String, tapRef: String) = {
    implicit val ec = context.dispatcher
    context.actorSelection(ref.path.address.toString + "/user/" + tapRef).resolveOne(5.seconds).onComplete {
      case Success(result) =>
        self ! StartTapProxy(id, result)
      case Failure(failure) =>
        logger.warn(s"Unable to resolve $id at $ref", failure)
    }
  }

  private def killTapProxy(id: String) = {
    activeTaps.get(id).foreach(_ ! PoisonPill)
  }

}

case class StartTapProxy(id: String, ref: ActorRef)
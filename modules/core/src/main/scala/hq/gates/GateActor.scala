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

import agent.flavors.files.{Cursor, ProducedMessage}
import agent.shared._
import akka.actor._
import akka.util.ByteString
import common.ToolExt.configHelper
import common.actors.{ActorWithConfigStore, AtLeastOnceDeliveryActor, PipelineWithStatesActor, SingleComponentActor}
import common.{BecomeActive, BecomePassive, JsonFrame}
import hq._
import play.api.libs.json.{JsNumber, JsValue, Json}

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}


case class RegisterSink(sinkRef: ActorRef)

private case class OriginMeta(originalCorrelationId: Long, sender: ActorRef)

class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithConfigStore
  with SingleComponentActor {


  private val correlationToOrigin: mutable.Map[Long, OriginMeta] = mutable.Map()
  private var sinks: Set[ActorRef] = Set()

  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  override def key = ComponentKey("gate/" + id)


  override def storageKey: Option[String] = Some(s"gate/$id")

  override def preStart(): Unit = {

    if (isPipelineActive)
      switchToCustomBehavior(flowMessagesHandlerForOpenGate)
    else
      switchToCustomBehavior(flowMessagesHandlerForClosedGate)

    super.preStart()

  }

  override def onInitialConfigApplied(): Unit = context.parent ! GateAvailable(key)


  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def becomeActive(): Unit = {
    topicUpdate(T_INFO, info)
    switchToCustomBehavior(flowMessagesHandlerForOpenGate)
  }

  override def becomePassive(): Unit = {
    topicUpdate(T_INFO, info)
    switchToCustomBehavior(flowMessagesHandlerForClosedGate)
  }

  def info = propsConfig.map { cfg =>
    Json.obj(
      "name" -> cfg ~> 'name,
      "text" -> s"some random text from $id",
      "state" -> (if (isPipelineActive) "active" else "passive")
    )
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
    case T_PROPS => topicUpdate(T_PROPS, propsConfig, singleTarget = Some(ref))
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
        case _ =>
          logger.info("Already stopped")
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
        case _ =>
          logger.info("Starting the gate " + self.toString())
          self ! BecomeActive()
      }
    case T_KILL =>
      removeConfig()
      self ! PoisonPill
  }

  override def canDeliverDownstreamRightNow: Boolean = isPipelineActive

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Fully acknowledged $correlationId ")
    correlationToOrigin.get(correlationId).foreach { origin =>
      logger.info(s"Ack ${origin.originalCorrelationId} with tap at ${origin.sender}")
      origin.sender ! Acknowledge(origin.originalCorrelationId)
    }
  }

  override def getSetOfActiveEndpoints: Set[ActorRef] = sinks


  def convert(id: Long, message: Any): Option[JsonFrame] = message match {
    case m@ProducedMessage(MessageWithAttachments(bs: ByteString, attachments), c: Cursor) =>
      Some(JsonFrame(Json.obj(
        "tags" -> Json.arr("source"),
        "id" -> id,
        "value" -> bs.utf8String,
        "source" -> attachments),
        ctx = Map[String, JsValue]("source.id" -> JsNumber(id))))
    case m: JsonFrame =>
      // TODO add route slip to the context
      Some(m)
    case x =>
      logger.warn(s"Unsupported message type at the gate  $id: $x")
      None
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {

  }

  private def flowMessagesHandlerForClosedGate: Receive = {
    case m: Acknowledgeable[_] =>
      sender ! GateStateUpdate(GateClosed())
  }

  private def canAcceptAnotherMessage = inFlightCount < 10 // TODO configurable

  private def flowMessagesHandlerForOpenGate: Receive = {
    case m: Acknowledgeable[_] =>
      if (canAcceptAnotherMessage) {
        logger.info(s"New message arrived at the gate $id ... ${m.id}")
        if (!correlationToOrigin.exists {
          case (_, OriginMeta(originalCorrelationId, ref)) =>
            originalCorrelationId == m.id && ref == sender()
        }) {
          convert(m.id, m.msg) foreach { msg =>
            val correlationId = deliverMessage(msg)
            correlationToOrigin += correlationId -> OriginMeta(m.id, sender())
          }
        } else {
          logger.info(s"Received duplicate message $id ${m.id}")
        }
      } else {
        logger.debug(s"Unable to accept another message, in flight count $inFlightCount")
      }
  }

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      logger.debug(s"Received state check from $ref, our state: $isPipelineActive")
      if (isPipelineActive) {
        ref ! GateStateUpdate(GateOpen())
      } else {
        ref ! GateStateUpdate(GateClosed())
      }
    case Terminated(ref) if sinks.contains(ref) =>
      logger.info(s"Sink is gone: $ref")
      sinks -= ref
    case RegisterSink(sinkRef) =>
      sinks += sender()
      context.watch(sinkRef)
      logger.info(s"New sink: ${sender()}")
  }
}


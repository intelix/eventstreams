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

import agent.controller.flow.Tools
import agent.flavors.files.{Cursor, ProducedMessage}
import agent.shared._
import akka.actor._
import akka.util.ByteString
import common.ToolExt.configHelper
import common._
import common.actors._
import hq._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._
import scalaz.{-\/, \/}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


case class RegisterSink(sinkRef: ActorRef)

private case class InflightMessage(originalCorrelationId: Long, originator: ActorRef, retentionPending: Boolean, deliveryPending: Boolean)


sealed trait GateState {
  def details: Option[String]
}

case class GateStateUnknown(details: Option[String] = None) extends GateState

case class GateStateOpen(details: Option[String] = None) extends GateState

case class GateStateClosed(details: Option[String] = None) extends GateState

case class GateStateReplay(details: Option[String] = None) extends GateState

case class GateStateError(details: Option[String] = None) extends GateState


class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[JsonFrame]
  with ActorWithConfigStore
  with SingleComponentActor
  with ActorWithDupTracking {


  private val correlationToOrigin: mutable.Map[Long, InflightMessage] = mutable.Map()
  var name = "default"
  var address = uuid.toString
  var initialState = "Closed"
  var retentionStorageKey = "default"
  var created = prettyTimeFormat(now)
  var maxInFlight = 100
  var acceptWithoutSinks = false
  var retentionPolicy: RetentionPolicy = new RetentionPolicyNone()
  var overflowPolicy: OverflowPolicy = new OverflowPolicyBackpressure()

  var currentState: GateState = GateStateUnknown(Some("Initialising"))


  private var sinks: Set[ActorRef] = Set()
  private var forwarderId: Option[String] = None
  private var forwarderActor: Option[ActorRef] = None

  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  override def key = ComponentKey(id)


  override def storageKey: Option[String] = Some(id)

  override def preStart(): Unit = {

    if (isPipelineActive)
      openGate()
    else
      closeGate()

    super.preStart()

  }


  def openGate(): Unit = {
    currentState = GateStateOpen(Some("ok"))
    switchToCustomBehavior(flowMessagesHandlerForOpenGate)
  }

  def closeGate(): Unit = {
    currentState = GateStateClosed()
    switchToCustomBehavior(flowMessagesHandlerForClosedGate)
  }

  override def postStop(): Unit = {
    forwarderActor.foreach(context.system.stop)
    super.postStop()
  }

  override def onInitialConfigApplied(): Unit = context.parent ! GateAvailable(key)


  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def becomeActive(): Unit = {
    openGate()
    topicUpdate(T_INFO, info)
  }

  override def becomePassive(): Unit = {
    closeGate()
    topicUpdate(T_INFO, info)
  }

  def stateAsString = currentState match {
    case GateStateUnknown(_) => "unknown"
    case GateStateOpen(_) => "active"
    case GateStateClosed(_) => "passive"
    case GateStateReplay(_) => "replay"
    case GateStateError(_) => "error"
  }

  def stateDetailsAsString = currentState.details match {
    case Some(x) => x
    case _ => ""
  }

  def info = propsConfig.map { cfg =>
    Json.obj(
      "name" -> name,
      "address" -> address,
      "initial" -> initialState,
      "overflow" -> overflowPolicy.info,
      "retention" -> retentionPolicy.getInfo,
      "sinceStateChange" -> prettyTimeSinceStateChange,
      "acceptWithoutSinks" -> acceptWithoutSinks,
      "created" -> created,
      "replaySupported" -> retentionPolicy.replaySupported,
      "state" -> stateAsString,
      "stateDetails" -> stateDetailsAsString
    )
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
    case T_PROPS => topicUpdate(T_PROPS, propsConfig, singleTarget = Some(ref))
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }


  def messageAllowance = if (maxInFlight - correlationToOrigin.size < 1) 1 else maxInFlight - correlationToOrigin.size

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_REPLAY =>
      lastRequestedState match {
        case Some(Passive()) =>
          Fail(message = Some("Gate must be started")).left
        case _ =>
          logger.info("Initiating replay ")
          retentionPolicy.initiateReplay(self, messageAllowance)
          OK().right
      }
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the gate")
          self ! BecomePassive()
          OK().right
        case _ =>
          logger.info("Already stopped")
          Fail(message = Some("Already stopped")).left
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
          Fail(message = Some("Already started")).left
        case _ =>
          logger.info("Starting the gate " + self.toString())
          self ! BecomeActive()
          OK().right
      }
    case T_KILL =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  override def canDeliverDownstreamRightNow: Boolean = isPipelineActive

  override def fullyAcknowledged(correlationId: Long, msg: JsonFrame): Unit = {
    logger.info(s"Delivered to all active sinks $correlationId ")
    correlationToOrigin.get(correlationId).foreach { origin =>
      correlationToOrigin += correlationId -> origin.copy(deliveryPending = false)
      checkCompleteness(correlationId)
    }
  }

  override def getSetOfActiveEndpoints: Set[ActorRef] = sinks


  def enrichInboundJsonFrame(inboundCorrelationId: Long, frame: JsonFrame) = {
    val eventId = frame.event ~> 'eventId | shortUUID
    val eventType = frame.event ~> 'eventType | "default";
    val timestamp = frame.event ++> 'ts | now
    var trace = (frame.event ##> 'trace).map(_.map(_.asOpt[String] | "")) | List()
    if (!trace.contains(id)) trace = trace :+ id

    JsonFrame(
      frame.event.set(
        __ \ 'eventId -> JsString(eventId),
        __ \ 'eventType -> JsString(eventType),
        __ \ 'ts -> JsNumber(timestamp)),
      frame.ctx + ("correlationId" -> JsNumber(inboundCorrelationId)) + ("processedTs" -> JsNumber(now)) + ("trace" -> Json.toJson(trace.toArray))
    )
  }

  def convertInboundPayload(id: Long, message: Any): Option[JsonFrame] = message match {
    case m@ProducedMessage(MessageWithAttachments(bs: ByteString, attachments), _, c: Cursor) =>
      logger.debug(s"Original message at the gate: ${bs.utf8String}")
      val json = attachments.set(__ \ "value" -> JsString(bs.utf8String))
      Some(enrichInboundJsonFrame(id, JsonFrame(json,
        ctx = Map[String, JsValue]())))
    case m: JsonFrame => Some(enrichInboundJsonFrame(id,m))
    case x =>
      logger.warn(s"Unsupported message type at the gate  $id: $x")
      None
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = propsConfig ~> 'name | "default"
    initialState = propsConfig ~> 'initialState | "Closed"
    maxInFlight = propsConfig +> 'maxInFlight | 10
    acceptWithoutSinks = propsConfig ?> 'acceptWithoutSinks | false
    address = props ~> 'address | key
    created = prettyTimeFormat(props ++> 'created | now)
    overflowPolicy = OverflowPolicyBuilder(propsConfig #> 'overflowPolicy)
    retentionPolicy = RetentionPolicyBuilder(propsConfig #> 'retentionPolicy)

    val newForwarderId = ActorTools.actorFriendlyId(address)
    forwarderId match {
      case None => reopenForwarder(newForwarderId)
      case Some(x) if x != newForwarderId => reopenForwarder(newForwarderId)
      case x => ()
    }

  }

  override def afterApplyConfig(): Unit = {
    topicUpdate(T_INFO, info)
    topicUpdate(T_PROPS, propsConfig)
  }

  private def reopenForwarder(newForwarderId: String) = {
    forwarderId = Some(newForwarderId)
    forwarderActor.foreach(context.system.stop)
    forwarderActor = Some(context.system.actorOf(Props(new Forwarder(self)), newForwarderId))
    logger.info(s"Gate forwarder started for $newForwarderId, actor $forwarderActor")
  }

  private def flowMessagesHandlerForClosedGate: Receive = {
    case m: Acknowledgeable[_] =>
      forwarderActor.foreach(_ ! RouteTo(sender(), GateStateUpdate(GateClosed())))
  }

  private def canAcceptAnotherMessage = currentState match {
    case GateStateOpen(_) => correlationToOrigin.size < maxInFlight
    case _ => false
  }

  private def canAcceptAnotherReplayMessage = currentState match {
    case GateStateOpen(_) => correlationToOrigin.size < maxInFlight
    case GateStateReplay(_) => correlationToOrigin.size < maxInFlight
    case _ => false
  }

  //  private def isDup(m: Acknowledgeable[_], sender: ActorRef) = correlationToOrigin.exists {
//    case (_, InflightMessage(originalCorrelationId, ref, _, _)) =>
//      originalCorrelationId == m.id && ref == sender
//  }

  private def flowMessagesHandlerForOpenGate: Receive = {
    case m: Acknowledgeable[_] =>
      if (canAcceptAnotherMessage) {
        forwarderActor.foreach(_ ! RouteTo(sender(), AcknowledgeAsReceived(m.id)))
        if (!isDup(sender(), m.id)) {
          logger.info(s"New unique message arrived at the gate $id ... ${m.id}")
          convertInboundPayload(m.id, m.msg) foreach { msg =>
            val correlationId = if (sinks.isEmpty && acceptWithoutSinks) generateCorrelationId(msg) else deliverMessage(msg)
            correlationToOrigin += correlationId ->
              InflightMessage(
                m.id,
                sender(),
                retentionPending = retentionPolicy.scheduleRetention(
                  correlationId, msg),
                deliveryPending = sinks.nonEmpty || !acceptWithoutSinks)
          }
        } else {
          logger.info(s"Received duplicate message at $id ${m.id}")
        }
      } else {
        logger.debug(s"Unable to accept another message, in flight count $inFlightCount")
        if (overflowPolicy.ackAndDrop) {
          forwarderActor.foreach(_ ! RouteTo(sender(), AcknowledgeAsProcessed(m.id)))
          logger.info(s"Dropped ${m.id} from ${sender()}")
        }
      }
    case ReplayedEvent(originalCId, msg) =>
      if (canAcceptAnotherReplayMessage) {
        logger.info(s"New replayed message arrived at the gate $id ... $originalCId")
        val frame = JsonFrame(Json.parse(msg).as[JsValue], Map())
        val correlationId = if (sinks.isEmpty && acceptWithoutSinks) generateCorrelationId(frame) else deliverMessage(frame)
        correlationToOrigin += correlationId ->
          InflightMessage(
            originalCId,
            sender(),
            retentionPending = false,
            deliveryPending = sinks.nonEmpty || !acceptWithoutSinks)
      } else {
        logger.debug(s"Unable to accept another message, in flight count $inFlightCount")
      }
    case ReplayEnd() =>
      currentState = GateStateOpen()
      topicUpdate(T_INFO, info)
    case ReplayFailed(error) =>
      currentState = GateStateError()
      topicUpdate(T_INFO, info)
    case ReplayStart() =>
      currentState = GateStateReplay(Some("in progress"))
      topicUpdate(T_INFO, info)

  }

  private def checkCompleteness(correlationId: Long) = correlationToOrigin.get(correlationId).foreach { m =>
    if (!m.deliveryPending && !m.retentionPending) {
      logger.info(s"Fully acknowledged $correlationId ")
      logger.info(s"Ack ${m.originalCorrelationId} with tap at ${m.originator}")
      forwarderActor.foreach(_ ! RouteTo(m.originator, AcknowledgeAsProcessed(m.originalCorrelationId)))
      correlationToOrigin -= correlationId
    }
  }

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      logger.debug(s"Received state check from $ref, our state: $isPipelineActive")
      if (isPipelineActive) {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateOpen())))
      } else {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateClosed())))
      }
    case Terminated(ref) if sinks.contains(ref) =>
      logger.info(s"Sink is gone: $ref")
      sinks -= ref
    case RegisterSink(sinkRef) =>
      sinks += sender()
      context.watch(sinkRef)
      logger.info(s"New sink: ${sender()}")
    case MessageStored(correlationId) =>
      logger.debug(s"Message $correlationId stored")
      correlationToOrigin.get(correlationId).foreach { m =>
        correlationToOrigin += correlationId -> m.copy(retentionPending = false)
        checkCompleteness(correlationId)
      }
  }


  trait RetentionPolicy {
    def initiateReplay(ref: ActorRef, limit: Int): \/[Fail, OK] = -\/(Fail(message = Some("No retention policy configured for the gate")))

    def scheduleRetention(correlationId: Long, m: JsonFrame): Boolean
    def replaySupported: Boolean
    def getInfo: String
  }

  trait OverflowPolicy {
    val ackAndDrop: Boolean = false

    def info: String
  }

  class RetentionPolicyNone extends RetentionPolicy {
    override def getInfo: String = "None"
    override def replaySupported = false
    override def scheduleRetention(correlationId: Long, m: JsonFrame): Boolean = false
  }

  trait BaseRetentionPolicy extends RetentionPolicy{
    def json: Option[JsValue]

    val count = json +> 'count | 1
    val indexPattern = json ~> 'indexPattern | "gate-${now:yyyy-MM-dd}"
    val eventType = json ~> 'eventType | "${eventType}"
    val replayIndexPattern = json ~> 'replayIndexPattern | "gate-${now:yyyy-MM-dd}"
    val replayEventType = json ~> 'replayEventType | ""

    override def replaySupported = true

    override def scheduleRetention(correlationId: Long, m: JsonFrame): Boolean = {
      val id = m.event ~> 'eventId | shortUUID
      val etype = Tools.macroReplacement(m, eventType)
      val idx = Tools.macroReplacement(m, indexPattern)
      RetentionManagerActor.path ! ScheduleStorage(self, correlationId, idx, etype, id, m.event)
      true
    }

    override def initiateReplay(ref: ActorRef, limit: Int): \/[Fail, OK] = {
      val idx = Tools.macroReplacement(Json.obj(), Map[String,JsValue](), replayIndexPattern)
      RetentionManagerActor.path ! InitiateReplay(ref, idx, replayEventType, limit)
      currentState = GateStateReplay(Some("awaiting"))
      topicUpdate(T_INFO, info)
      OK().right
    }
  }

  class RetentionPolicyDays(val json: Option[JsValue]) extends BaseRetentionPolicy {
    override def getInfo: String = s"$count day(s)"
  }

  class RetentionPolicyCount(val json: Option[JsValue]) extends BaseRetentionPolicy {
    override def getInfo: String = s"$count event(s)"
  }

  class OverflowPolicyBackpressure extends OverflowPolicy {
    override def info: String = "Backpressure"
  }

  class OverflowPolicyDrop() extends OverflowPolicy {
    override def info: String = "Drop"

    override val ackAndDrop: Boolean = true
  }

  object RetentionPolicyBuilder {
    def apply(j: Option[JsValue]) = j ~> 'type match {
      case Some("days") => new RetentionPolicyDays(j)
      case Some("count") => new RetentionPolicyCount(j)
      case _ => new RetentionPolicyNone()
    }
  }

  object OverflowPolicyBuilder {
    def apply(j: Option[JsValue]) = j ~> 'type match {
      case Some("drop") =>  new OverflowPolicyDrop()
      case _ => new OverflowPolicyBackpressure()
    }
  }


}

case class RouteTo(ref: ActorRef, msg: Any)

class Forwarder(forwardTo: ActorRef) extends ActorWithComposableBehavior {
  override def commonBehavior: Receive = {
    case RouteTo(ref, msg) => ref ! msg
    case x => forwardTo.forward(x)
  }
}


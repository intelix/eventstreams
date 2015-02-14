/*
 * Copyright 2014-15 Intelix Pty Ltd
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

package eventstreams.gates

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import eventstreams.retention._
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._
import scalaz.\/

trait GateSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Gate.Gate"
}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}

private case class InflightMessage(originalCorrelationId: Long, originator: ActorRef, retentionPending: Boolean, deliveryPending: Boolean)

sealed trait InternalGateState {
  def details: Option[String]
}

case class GateStateUnknown(details: Option[String] = None) extends InternalGateState

case class GateStateOpen(details: Option[String] = None) extends InternalGateState

case class GateStateClosed(details: Option[String] = None) extends InternalGateState

case class GateStateReplay(details: Option[String] = None) extends InternalGateState

case class GateStateError(details: Option[String] = None) extends InternalGateState


class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with ActorWithConfigStore
  with RouteeActor
  with ActorWithDupTracking
  with ActorWithPeriodicalBroadcasting
  with ActorWithTicks
  with WithMetrics
  with GateSysevents
  with WithSyseventPublisher {


  private val correlationToOrigin: mutable.Map[Long, InflightMessage] = mutable.Map()
  var name = "default"
  var address = uuid.toString
  var initialState = "Closed"
  var retentionStorageKey = "default"
  var created = prettyTimeFormat(now)
  var maxInFlight = 1000
  var acceptWithoutSinks = false

  var replayIndexPattern = "default-${eventts:yyyy-MM}"
  var replayEventType = "event"

  var eventSequenceCounter = now

  var retainedDataCount: Option[Long] = None

  var currentState: InternalGateState = GateStateUnknown(Some("Initialising"))

  val _rateMeter = metrics.meter(s"gate.$id.rate")

  val activeEventsources = mutable.Map[ActorRef, Long]()


  private var sinks: Set[ActorRef] = Set()
  private var forwarderId: Option[String] = None
  private var forwarderActor: Option[ActorRef] = None

  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  override def key = ComponentKey(id)


  override def storageKey: Option[String] = Some(id)


  override def preStart(): Unit = {

    if (isComponentActive)
      openGate()
    else
      closeGate()

    super.preStart()

  }


  private def publishInfo() = {
    T_INFO !! info
    T_STATS !! stats
  }

  private def publishProps() = T_PROPS !! propsConfig


  override def processTick(): Unit = {
    super.processTick()
    clearActiveEventsourceList()
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
    publishInfo()
  }

  override def becomePassive(): Unit = {
    closeGate()
    publishInfo()
  }

  def stateAsString = currentState match {
    case GateStateUnknown(_) => "unknown"
    case GateStateOpen(_) => "active"
    case GateStateClosed(_) => "passive"
    case GateStateReplay(_) => "replay"
    case GateStateError(_) => "error"
  }

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def retainedDataAsString = retainedDataCount match {
    case Some(v) => "~" + v
    case _ => "N/A"
  }

  def info = Some(Json.obj(
    "name" -> name,
    "address" -> address,
    "addressFull" -> (forwarderActor.map(_.toString) | "N/A"),
    "initial" -> initialState,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "acceptWithoutSinks" -> acceptWithoutSinks,
    "created" -> created,
    "replaySupported" -> true,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString,
    "sinks" -> sinks.size
  ))

  def stats = currentState match {
    case GateStateOpen(_) | GateStateReplay(_) => Some(Json.obj(
      "rate" -> ("%.2f" format _rateMeter.oneMinuteRate),
      "mrate" -> ("%.2f" format _rateMeter.meanRate),
      "activeDS" -> activeEventsources.size,
      "inflight" -> correlationToOrigin.size,
      "retained" -> retainedDataAsString
    ))
    case _ => Some(Json.obj(
      "activeDS" -> activeEventsources.size,
      "inflight" -> correlationToOrigin.size,
      "retained" -> retainedDataAsString
    ))
  }


  def initiateReplay(ref: ActorRef, limit: Int): \/[Fail, OK] = {
    val idx = JSONTools.macroReplacement(EventFrame(), replayIndexPattern)
    RetentionManagerActor.path ! InitiateReplay(ref, idx, replayEventType, limit)
    currentState = GateStateReplay(Some("awaiting"))
    publishInfo()
    OK().right
  }


  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case TopicKey(x) => logger.debug(s"Unknown topic $x")
  }


  def messageAllowance = if (maxInFlight - correlationToOrigin.size < 1) 1 else maxInFlight - correlationToOrigin.size

  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_REPLAY =>
      lastRequestedState match {
        case Some(Passive()) =>
          Fail(message = Some("Gate must be started")).left
        case _ =>
          logger.info("Initiating replay ")
          initiateReplay(self, messageAllowance)
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
    case T_REMOVE =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  override def canDeliverDownstreamRightNow: Boolean = isComponentActive

  override def fullyAcknowledged(correlationId: Long, msg: EventFrame): Unit = {
    logger.info(s"Delivered to all active sinks $correlationId ")
    correlationToOrigin.get(correlationId).foreach { origin =>
      correlationToOrigin += correlationId -> origin.copy(deliveryPending = false)
      checkCompleteness(correlationId)
    }
  }

  override def getSetOfActiveEndpoints: Set[ActorRef] = sinks


  def nextEventSequence() = {
    eventSequenceCounter = eventSequenceCounter + 1
    eventSequenceCounter
  }

  def enrichInboundJsonFrame(inboundCorrelationId: Long, frame: EventFrame) = {
    val eventId = frame.eventId | shortUUID
    val eventSeq = frame ++> 'eventSeq | nextEventSequence()
    val eventType = frame ~> 'eventType | "default"
    val timestamp = frame ++> 'ts | now
    var trace = (frame ##> 'trace).map(_.map(_.asString | "")) | List()
    if (!trace.contains(id)) trace = trace :+ id

    frame + ('eventId -> eventId) + ('eventSeq -> eventSeq) + ('ts -> timestamp)
  }

  def convertInboundPayload(id: Long, message: Any): Option[EventFrame] = message match {
    case m: EventFrame => Some(enrichInboundJsonFrame(id, m))
    case x =>
      logger.warn(s"Unsupported message type at the gate  $id: $x")
      None
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name | "default"
    initialState = props ~> 'initialState | "Closed"
    maxInFlight = props +> 'maxInFlight | 1000
    acceptWithoutSinks = props ?> 'acceptWithoutSinks | false
    address = props ~> 'address | key
    created = prettyTimeFormat(props ++> 'created | now)

    replayIndexPattern = props #> 'retentionPolicy ~> 'replayIndexPattern | "default-*"
    replayEventType = props #> 'retentionPolicy ~> 'replayEventType | "event"

    val newForwarderId = ActorTools.actorFriendlyId(address)
    forwarderId match {
      case None => reopenForwarder(newForwarderId)
      case Some(x) if x != newForwarderId => reopenForwarder(newForwarderId)
      case x => ()
    }

  }

  override def afterApplyConfig(): Unit = {
    publishInfo()
    publishProps()
  }

  private def reopenForwarder(newForwarderId: String) = {
    forwarderId = Some(newForwarderId)
    forwarderActor.foreach(context.system.stop)
    forwarderActor = Some(context.system.actorOf(Props(new Forwarder(self)), newForwarderId))
    logger.info(s"Gate forwarder started for $newForwarderId, actor $forwarderActor")
  }

  private def flowMessagesHandlerForClosedGate: Receive = {
    case m: Acknowledgeable[_] =>
      updateActiveEventsourceListWith(sender())
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

  private def clearActiveEventsourceList() = activeEventsources.collect {
    case (a, l) if now - l > 1.minute.toMillis => a
  } foreach activeEventsources.remove

  private def updateActiveEventsourceListWith(ref: ActorRef) = activeEventsources += ref -> now

  private def flowMessagesHandlerForOpenGate: Receive = {
    case m: Acknowledgeable[_] =>
      updateActiveEventsourceListWith(sender())
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
                retentionPending = false,
                deliveryPending = sinks.nonEmpty || !acceptWithoutSinks)
          }
        } else {
          logger.info(s"Received duplicate message at $id ${m.id}")
        }
      } else {
        logger.debug(s"Unable to accept another message, in flight count " + correlationToOrigin.size)
      }
    case ReplayedEvent(originalCId, msg) =>
      if (canAcceptAnotherReplayMessage) {
        logger.info(s"New replayed message arrived at the gate $id ... $originalCId")
        val frame = EventFrameConverter.fromJson(Json.parse(msg))
        val correlationId = if (sinks.isEmpty && acceptWithoutSinks) generateCorrelationId(frame) else deliverMessage(frame)
        correlationToOrigin += correlationId ->
          InflightMessage(
            originalCId,
            sender(),
            retentionPending = false,
            deliveryPending = sinks.nonEmpty || !acceptWithoutSinks)
      } else {
        logger.debug(s"Unable to accept another replayed message, in flight count " + correlationToOrigin.size)
      }
    case ReplayEnd() =>
      currentState = GateStateOpen()
      publishInfo()
    case ReplayFailed(error) =>
      currentState = GateStateError()
      publishInfo()
    case ReplayStart() =>
      currentState = GateStateReplay(Some("in progress"))
      publishInfo()

  }

  private def checkCompleteness(correlationId: Long) = correlationToOrigin.get(correlationId).foreach { m =>
    if (!m.deliveryPending && !m.retentionPending) {
      logger.info(s"Fully acknowledged $correlationId ")
      logger.info(s"Ack ${m.originalCorrelationId} with tap at ${m.originator}")
      forwarderActor.foreach(_ ! RouteTo(m.originator, AcknowledgeAsProcessed(m.originalCorrelationId)))
      correlationToOrigin -= correlationId
      _rateMeter.mark()
    }
  }


  override def onTerminated(ref: ActorRef): Unit = {
    if (sinks.contains(ref)) {
      logger.info(s"Sink is gone: $ref")
      sinks -= ref
      publishInfo()
    }
    super.onTerminated(ref)
  }

  private def messageHandler: Receive = {
    case RetainedCount(count) => retainedDataCount = Some(count)
    case GateStateCheck(ref) =>
      logger.debug(s"Received state check from $ref, our state: $isComponentActive")
      if (isComponentActive) {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateOpen())))
      } else {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateClosed())))
      }
    case RegisterSink(sinkRef) =>
      sinks += sender()
      context.watch(sinkRef)
      logger.info(s"New sink: ${sender()}")
      publishInfo()
  }


  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => stats, T_STATS !! _)
  )
}

case class RouteTo(ref: ActorRef, msg: Any)

class Forwarder(forwardTo: ActorRef) extends ActorWithComposableBehavior {
  override def commonBehavior: Receive = {
    case RouteTo(ref, msg) => ref ! msg
    case x => forwardTo.forward(x)
  }

  override def componentId: String = "Gate.Forwarder"
}


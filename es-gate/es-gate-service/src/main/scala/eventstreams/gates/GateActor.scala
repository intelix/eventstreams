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

import java.util.concurrent.TimeUnit

import _root_.core.sysevents.SyseventOps.stringToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._

trait GateSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with AtLeastOnceDeliveryActorSysevents {
  val GateConfigured = "GateConfigured".trace
  val NewMessageReceived = "NewMessageReceived".trace
  val DuplicateMessageReceived = "DuplicateMessageReceived".trace
  val MessageIgnored = "MessageIgnored".trace
  val InflightsPurged = "InflightsPurged".info
  val SinkConnected = "SinkConnected".info
  val SinkDisconnected = "SinkDisconnected".info
  val ForwarderStarted = "ForwarderStarted".info
  val UnsupportedMessageType = "UnsupportedMessageType".warn

  override def componentId: String = "Gate.Gate"

}

object GateActor extends GateSysevents {
  def props(id: String, config: ModelConfigSnapshot) = Props(new GateActor(id, config))

  def start(id: String, config: ModelConfigSnapshot)(implicit f: ActorRefFactory) = f.actorOf(props(id, config), ActorTools.actorFriendlyId(id))
}

sealed trait InternalGateState {
  def details: Option[String]
}

case class GateStateUnknown(details: Option[String] = None) extends InternalGateState

case class GateStateOpen(details: Option[String] = None) extends InternalGateState

case class GateStateClosed(details: Option[String] = None) extends InternalGateState

case class GateStateError(details: Option[String] = None) extends InternalGateState


class GateActor(val entityId: String, val initialConfig: ModelConfigSnapshot)
  extends ActorWithActivePassiveBehaviors
  with AtLeastOnceDeliveryActor[EventFrame]
  with RouteeModelInstance
  with RouteeWithStartStopHandler
  with ActorWithDupTracking
  with ActorWithPeriodicalBroadcasting
  with ActorWithTicks
  with WithCHMetrics
  with ActorWithInstrumentationEnabled
  with GateSysevents
  with WithSyseventPublisher {


  override lazy val sensorComponentSubId: Option[String] = Some(name)
  val name = propsConfig ~> 'name | "default"
  val inFlightThreshold = propsConfig +> 'inFlightThreshold | 1000
  val noSinkDropMessages = propsConfig ?> 'noSinkDropMessages | false
  val address = propsConfig ~> 'address | entityId
  val created = prettyTimeFormat(metaConfig ++> 'created | now)
  val resendInterval = FiniteDuration(propsConfig ++> 'unacknowledgedMessagesResendIntervalSec | 5, TimeUnit.SECONDS)
  val forwarderId = ActorTools.actorFriendlyId(address)
  val _rateMeter = metricRegistry.meter(s"gate.$entityId.rate")
  val activeEventsources = mutable.Map[ActorRef, Long]()
  var eventSequenceCounter = now
  var currentState: InternalGateState = GateStateUnknown(Some("Initialising"))
  var sinks: Set[ActorRef] = Set()
  var forwarderActor: Option[ActorRef] = None

  override def configUnacknowledgedMessagesResendInterval = resendInterval

  override def commonFields: Seq[(Symbol, Any)] =
    super.commonFields ++ Seq('Name -> name, 'State -> currentState, 'ID -> entityId, 'Address -> address, 'Sinks -> sinks.size, 'Delivering -> canDeliverDownstreamRightNow)


  override def preStart(): Unit = {
    GateConfigured >>()

    openForwarder()

    if (metaConfig ?> 'lastStateActive | false) {
      becomeActive()
    } else {
      becomePassive()
    }

    super.preStart()

  }

  override def onCommand(maybeData: Option[JsValue]): CommandHandler = super.onCommand(maybeData) orElse {
    case T_PURGE =>
      val inFlightNow = inFlightCount
      purgeInflights()
      InflightsPurged >> ('Count -> inFlightNow)
      OK(message = Some(s"Purged $inFlightNow messages"))
  }


  override def processTick(): Unit = {
    super.processTick()
    clearActiveEventsourceList()
  }


  override def batchAggregationKeyFor(msg: EventFrame): Option[String] =
    for (
      key <- msg.streamKey;
      seed <- msg.streamSeed
    ) yield key + ":" + seed

  def openGate(): Unit = {
    currentState = GateStateOpen(Some("ok"))
    switchToCustomBehavior(flowMessagesHandlerForOpenGate)
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = true))

  }

  def closeGate(): Unit = {
    currentState = GateStateClosed()
    switchToCustomBehavior(flowMessagesHandlerForClosedGate)
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = false))

  }

  override def postStop(): Unit = {
    forwarderActor.foreach(context.system.stop)
    super.postStop()
  }


  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def onBecameActive(): Unit = {
    openGate()
    publishInfo()
  }

  override def onBecamePassive(): Unit = {
    closeGate()
    publishInfo()
  }

  def stateAsString = currentState match {
    case GateStateUnknown(_) => "unknown"
    case GateStateOpen(_) => "active"
    case GateStateClosed(_) => "passive"
    case GateStateError(_) => "error"
  }

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  override def info = Some(Json.obj(
    "name" -> name,
    "address" -> address,
    "addressFull" -> (forwarderActor.map(_.toString()) | "N/A"),
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "noSinkDropMessages" -> noSinkDropMessages,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString,
    "sinks" -> sinks.size
  ))

  override def stats = currentState match {
    case GateStateOpen(_) => Some(Json.obj(
      "rate" -> ("%.2f" format _rateMeter.getOneMinuteRate),
      "mrate" -> ("%.2f" format _rateMeter.getMeanRate),
      "activeDS" -> activeEventsources.size,
      "inflight" -> inFlightCount
    ))
    case _ => Some(Json.obj(
      "activeDS" -> activeEventsources.size,
      "inflight" -> inFlightCount
    ))
  }


  override def canDeliverDownstreamRightNow: Boolean = isComponentActive && sinks.nonEmpty

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

    /*  TODO  Revisit tracing
    var trace = (frame ##> 'trace).map(_.map(_.asString | "")) | List()
    if (!trace.contains(entityId)) trace = trace :+ entityId
    */

    frame + ('eventId -> eventId) + ('eventSeq -> eventSeq) + ('ts -> timestamp)
  }

  def convertInboundPayload(id: Long, message: Any): Seq[EventFrame] = message match {
    case Batch(e) => e.flatMap(convertInboundPayload(id, _))
    case m: EventFrame => Seq(enrichInboundJsonFrame(id, m))
    case x =>
      UnsupportedMessageType >> ('Type -> x.getClass)
      Seq()
  }

  override def onTerminated(ref: ActorRef): Unit = {
    if (sinks.contains(ref)) {
      SinkDisconnected >> ('Ref -> ref)
      sinks -= ref
      publishInfo()
    }
    super.onTerminated(ref)
  }

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => stats, T_STATS !! _)
  )

  override def modelEntryInfo: Model = GateAvailable(entityId, self, name)

  private def openForwarder() = {
    forwarderActor.foreach(context.system.stop)
    forwarderActor = Some(context.system.actorOf(Props(new Forwarder(self)), forwarderId))
    ForwarderStarted >> ('ForwarderId -> forwarderId)
  }

  private def flowMessagesHandlerForClosedGate: Receive = {
    case m: Acknowledgeable[_] =>
      updateActiveEventsourceListWith(sender())
      forwarderActor.foreach(_ ! RouteTo(sender(), GateStateUpdate(GateClosed())))
  }

  private def canAcceptAnotherMessage = currentState match {
    case GateStateOpen(_) => inFlightCount < inFlightThreshold
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
        forwarderActor.foreach(_ ! RouteTo(sender(), AcknowledgeAsProcessed(m.id)))
        if (!isDup(sender(), m.id)) {
          NewMessageReceived >> ('MessageId -> m.id)
          if (sinks.nonEmpty || !noSinkDropMessages) {
            val seq = convertInboundPayload(m.id, m.msg)
            seq foreach { msg =>
              deliverMessage(msg)
            }
            if (seq.nonEmpty) _rateMeter.mark(seq.size)
          }
        } else {
          DuplicateMessageReceived >> ('MessageId -> m.id)
        }
      } else {
        MessageIgnored >>('MessageId -> m.id, 'Reason -> "Backlog", 'InFlightCount -> inFlightCount)
      }

  }

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      if (isComponentActive) {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateOpen())))
      } else {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateClosed())))
      }
    case RegisterSink(sinkRef) =>
      sinks += sinkRef
      context.watch(sinkRef)
      SinkConnected >> ('Ref -> sinkRef)
      publishInfo()
    case UnregisterSink(sinkRef) =>
      sinks -= sinkRef
      context.unwatch(sinkRef)
      SinkDisconnected >> ('Ref -> sinkRef)
  }
}

case class RouteTo(ref: ActorRef, msg: Any)

class Forwarder(forwardTo: ActorRef) extends ActorWithComposableBehavior {
  override def commonBehavior: Receive = {
    case RouteTo(ref, msg) => ref ! msg
    case x => forwardTo.forward(x)
  }

  override def componentId: String = "Gate.Forwarder"
}


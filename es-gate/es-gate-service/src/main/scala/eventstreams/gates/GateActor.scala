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

import _root_.core.sysevents.SyseventOps.stringToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scalaz.Scalaz._

trait GateSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Gate.Gate"

  val NewMessageReceived = "NewMessageReceived".trace
  val DuplicateMessageReceived = "DuplicateMessageReceived".trace
  val MessageIgnored = "MessageIgnored".trace

  val SinkConnected = "SinkConnected".info
  val SinkDisconnected = "SinkDisconnected".info

  val ForwarderStarted = "ForwarderStarted".info
  val UnsupportedMessageType = "UnsupportedMessageType".warn
  
}

object GateActor {
  def props(id: String) = Props(new GateActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}

sealed trait InternalGateState {
  def details: Option[String]
}

case class GateStateUnknown(details: Option[String] = None) extends InternalGateState

case class GateStateOpen(details: Option[String] = None) extends InternalGateState

case class GateStateClosed(details: Option[String] = None) extends InternalGateState

case class GateStateError(details: Option[String] = None) extends InternalGateState


class GateActor(id: String)
  extends PipelineWithStatesActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with ActorWithConfigStore
  with RouteeModelInstance
  with RouteeWithStartStopHandler
  with ActorWithDupTracking
  with ActorWithPeriodicalBroadcasting
  with ActorWithTicks
  with WithMetrics
  with GateSysevents
  with WithSyseventPublisher {


  var name = "default"
  var address = uuid.toString
  var created = prettyTimeFormat(now)
  var inFlightThreshold = 1000
  var noSinkDropMessages = false

  var eventSequenceCounter = now

  var currentState: InternalGateState = GateStateUnknown(Some("Initialising"))

  val _rateMeter = metrics.meter(s"gate.$id.rate")

  val activeEventsources = mutable.Map[ActorRef, Long]()


  private var sinks: Set[ActorRef] = Set()
  private var forwarderId: Option[String] = None
  private var forwarderActor: Option[ActorRef] = None

  override def configUnacknowledgedMessagesResendInterval: FiniteDuration = 10.seconds

  override def key = ComponentKey(id)


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('Name -> name, 'State -> currentState, 'Key -> id)

  override def storageKey: Option[String] = Some(id)


  override def preStart(): Unit = {

    if (isComponentActive)
      openGate()
    else
      closeGate()

    super.preStart()

  }

  override def processTick(): Unit = {
    super.processTick()
    clearActiveEventsourceList()
  }

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


  override def publishAvailable(): Unit = context.parent ! GateAvailable(key, self, name)

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
      "rate" -> ("%.2f" format _rateMeter.oneMinuteRate),
      "mrate" -> ("%.2f" format _rateMeter.meanRate),
      "activeDS" -> activeEventsources.size,
      "inflight" -> inFlightCount
    ))
    case _ => Some(Json.obj(
      "activeDS" -> activeEventsources.size,
      "inflight" -> inFlightCount
    ))
  }


  override def canDeliverDownstreamRightNow: Boolean = isComponentActive

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

  def convertInboundPayload(id: Long, message: Any): Seq[EventFrame] = message match {
    case Batch(e) => e.flatMap(convertInboundPayload(id, _))
    case m: EventFrame => Seq(enrichInboundJsonFrame(id, m))
    case x =>
      UnsupportedMessageType >> ('Type -> x.getClass)
      Seq()
  }

  override def applyConfig(key: String, props: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit = {
    name = props ~> 'name | "default"

    inFlightThreshold = props +> 'inFlightThreshold | 1000

    noSinkDropMessages = props ?> 'acceptWithoutSinks | false
    address = props ~> 'address | key
    created = prettyTimeFormat(meta ++> 'created | now)


    val newForwarderId = ActorTools.actorFriendlyId(address)
    forwarderId match {
      case None => reopenForwarder(newForwarderId)
      case Some(x) if x != newForwarderId => reopenForwarder(newForwarderId)
      case x => ()
    }

    if (meta ?> 'lastStateActive | false) {
      becomeActive()
    }

  }

  private def reopenForwarder(newForwarderId: String) = {
    forwarderId = Some(newForwarderId)
    forwarderActor.foreach(context.system.stop)
    forwarderActor = Some(context.system.actorOf(Props(new Forwarder(self)), newForwarderId))
    ForwarderStarted >> ('ForwarderId -> newForwarderId)
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
        MessageIgnored >> ('MessageId -> m.id, 'Reason -> "Backlog", 'InFlightCount -> inFlightCount)
      }

  }

  override def onTerminated(ref: ActorRef): Unit = {
    if (sinks.contains(ref)) {
      SinkDisconnected >> ('Ref -> ref)
      sinks -= ref
      publishInfo()
    }
    super.onTerminated(ref)
  }

  private def messageHandler: Receive = {
    case GateStateCheck(ref) =>
      if (isComponentActive) {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateOpen())))
      } else {
        forwarderActor.foreach(_ ! RouteTo(ref, GateStateUpdate(GateClosed())))
      }
    case RegisterSink(sinkRef) =>
      sinks += sender()
      context.watch(sinkRef)
      SinkConnected >> ('Ref -> sender)
      publishInfo()
  }


  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => stats, T_STATS !! _)
  )

  override def fullyAcknowledged(correlationId: Long, msg: Batch[EventFrame]): Unit = {}
}

case class RouteTo(ref: ActorRef, msg: Any)

class Forwarder(forwardTo: ActorRef) extends ActorWithComposableBehavior {
  override def commonBehavior: Receive = {
    case RouteTo(ref, msg) => ref ! msg
    case x => forwardTo.forward(x)
  }

  override def componentId: String = "Gate.Forwarder"
}


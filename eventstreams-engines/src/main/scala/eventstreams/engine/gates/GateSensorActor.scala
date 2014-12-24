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

package eventstreams.engine.gates

import akka.actor._
import akka.stream.FlowMaterializer
import eventstreams.core.Tools.configHelper
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.engine.signals.{Signal, SignalLevel}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.{-\/, \/-}

object GateSensorActor {
  def props(id: String) = Props(new GateSensorActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


sealed trait GateSensorState {
  def details: Option[String]
}

case class GateSensorStateUnknown(details: Option[String] = None) extends GateSensorState

case class GateSensorStateActive(details: Option[String] = None) extends GateSensorState

case class GateSensorStatePassive(details: Option[String] = None) extends GateSensorState

case class GateSensorStateError(details: Option[String] = None) extends GateSensorState


class GateSensorActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with SingleComponentActor
  with WithSignalAccounting
  with ActorWithPeriodicalBroadcasting
  with NowProvider {

  implicit val mat = FlowMaterializer()
  implicit val dispatcher = context.system.dispatcher
  var name = "default"
  var initialState = "Active"
  var created = prettyTimeFormat(now)
  var currentState: GateSensorState = GateSensorStateUnknown(Some("Initialising"))
  var condition: Condition = Condition.neverTrue
  var occurrenceCondition: OccurrenceCondition = OccurrenceConditionNone()
  var occurrenceCount: Int = 0
  var occurrenceWatchPeriodSec: Int = 0
  var title: Option[String] = None
  var body: Option[String] = None
  var correlationIdTemplate: Option[String] = None
  var icon: Option[String] = None
  var level: SignalLevel = SignalLevel.default()
  var signalClass: String = "default"
  var signalSubclass: Option[String] = None
  var timestampSource: Option[String] = None

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def onInitialConfigApplied(): Unit = context.parent ! GateSensorAvailable(key)

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior


  override def preStart(): Unit = {
    super.preStart()
  }

  def publishInfo() = {
    T_INFO !! info
    T_STATS !! stats
  }

  def publishStats() = {
    T_STATS !! stats
  }

  def publishProps() = T_PROPS !! propsConfig

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case GateSensorStateUnknown(_) => "unknown"
    case GateSensorStateActive(_) => "active"
    case GateSensorStatePassive(_) => "passive"
    case GateSensorStateError(_) => "error"
  }

  def stats = currentState match {
    case GateSensorStateActive(_) => Some(Json.obj(
      "current" -> accountedSignalsCount,
      "total" -> totalSignalsCount
    ))
    case _ => Some(Json.obj())
  }

  def info = Some(Json.obj(
    "name" -> name,
    "initial" -> initialState,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString,
    "class" -> signalClass,
    "subclass" -> (signalSubclass | "-"),
    "level" -> level.name
  ))


  override def becomeActive(): Unit = {
    startSensor()
    publishInfo()
  }

  override def becomePassive(): Unit = {
    stopSensor()
    publishInfo()
  }

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case T_STATS => publishStats()
  }


  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the sensor")
          self ! BecomePassive()
          \/-(OK())
        case _ =>
          logger.info("Already stopped")
          -\/(Fail("Already stopped"))
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
          -\/(Fail("Already started"))
        case _ =>
          logger.info("Starting the sensor " + self.toString())
          self ! BecomeActive()
          \/-(OK())
      }
    case T_KILL =>
      terminateSensor(Some("Sensor being deleted"))
      removeConfig()
      self ! PoisonPill
      \/-(OK())
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("No data");
        result <- updateAndApplyConfigProps(data)
      ) yield result
  }

  def stopSensor() = {
    currentState = GateSensorStatePassive()
    resetSignalAccounting()
    logger.debug(s"Sensor stopped")
  }

  override def signalAccountingPeriodSec: Int = occurrenceWatchPeriodSec

  override def applyConfig(key: String, config: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    initialState = config ~> 'initialState | "Closed"
    created = prettyTimeFormat(config ++> 'created | now)

    condition = SimpleCondition(config ~> 'simpleCondition) | Fail("Unable to build condition").left match {
      case -\/(fail) =>
        currentState = GateSensorStateError(fail.message)
        logger.info(s"Unable to build sensor $id: failed with $fail")
        Condition.neverTrue
      case \/-(cond) => cond
    }

    occurrenceCondition = (config ~> 'occurrenceCond | "None").toLowerCase match {
      case "less than" => OccurrenceConditionLessThan()
      case "more than" => OccurrenceConditionMoreThan()
      case "exactly" => OccurrenceConditionExactly()
      case _ => OccurrenceConditionNone()
    }

    occurrenceCount = config +> 'occurrenceCount | -1
    occurrenceWatchPeriodSec = config +> 'occurrenceWatchPeriodSec | 0

    title = config ~> 'title
    body = config ~> 'body
    icon = config ~> 'icon
    correlationIdTemplate = config ~> 'correlationIdTemplate
    timestampSource = config ~> 'timestampSource
    signalClass = config ~> 'signalClass | "default"
    signalSubclass = config ~> 'signalSubclass
    level = SignalLevel.fromString(config ~> 'level | "Very low")

  }

  def sendSignal(s: Signal) = {
    logger.debug(s"Sending signal $s")
  }

  def eventToSignal(e: JsonFrame): Signal = {

    val eventId = e.event ~> 'eventId | "undefined"
    val ts = timestampSource.flatMap { tsSource => Tools.locateFieldValue(e, tsSource).asOpt[Long]} | now

    val s = Signal("", 0, ts,
      eventId, level, signalClass, signalSubclass,
      correlationIdTemplate.map(Tools.macroReplacement(e, _)),
      None, None, None,
      title.map(Tools.macroReplacement(e, _)),
      body.map(Tools.macroReplacement(e, _)),
      icon.map(Tools.macroReplacement(e, _)), None)

    logger.debug(s"Candidate signal $s")

    accountSignal(s)

    s
  }

  def produceSignalFrom(e: JsonFrame): Option[Signal] =
    if (condition.metFor(e).isRight) {
      val signal = eventToSignal(e)
      if (occurrenceCondition.isMetFor(signal, occurrenceCount, accountedSignalsCount)) Some(signal) else None
    } else None

  override def afterApplyConfig(): Unit = {

    if (isPipelineActive)
      startSensor()
    else
      stopSensor()

    publishProps()
    publishInfo()
  }

  private def handler: Receive = {
    case f: JsonFrame => produceSignalFrom(f) foreach sendSignal
  }

  private def terminateSensor(reason: Option[String]) = {
    stopSensor()
  }

  private def startSensor() = {
    logger.debug(s"Sensor started")
    resetSignalAccounting()
    currentState = GateSensorStateActive(Some("ok"))
  }

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => stats, T_STATS !! _)
  )
}


sealed trait OccurrenceCondition {
  def isMetFor(s: Signal, count: Int, currentCount: Long): Boolean
}

case class OccurrenceConditionNone() extends OccurrenceCondition {
  override def isMetFor(s: Signal, count: Int, currentCount: Long): Boolean = true
}

case class OccurrenceConditionMoreThan() extends OccurrenceCondition {
  override def isMetFor(s: Signal, count: Int, currentCount: Long): Boolean = count > -1 && currentCount > count
}

case class OccurrenceConditionLessThan() extends OccurrenceCondition {
  override def isMetFor(s: Signal, count: Int, currentCount: Long): Boolean = count > -1 && currentCount < count
}

case class OccurrenceConditionExactly() extends OccurrenceCondition {
  override def isMetFor(s: Signal, count: Int, currentCount: Long): Boolean = count > -1 && currentCount == count
}
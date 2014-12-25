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

package eventstreams.engine.flows.core

import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.core.Tools._
import eventstreams.core.Types._
import eventstreams.core._
import eventstreams.core.actors.{ActorWithTicks, SubscribingPublisherActor}
import eventstreams.engine.gates.WithOccurrenceAccounting
import eventstreams.engine.signals.{Signal, SignalLevel}
import eventstreams.plugins.essentials.DateInstructionConstants
import org.joda.time.DateTime
import play.api.libs.json.{JsString, JsValue, Json}

import scala.collection.mutable
import scalaz.Scalaz._
import scalaz._

class SignalSensorInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "sensor"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      signalClass <- props ~> 'signalClass \/> Fail(s"Invalid sensor instruction. Missing 'signalClass' value. Contents: ${Json.stringify(props)}")
    ) yield SignalSensorInstructionActor.props(signalClass, props)

}


private object SignalSensorInstructionActor {
  def props(signalClass: String, config: JsValue) = Props(new SignalSensorInstructionActor(signalClass, config))
}

private class SignalSensorInstructionActor(signalClass: String, props: JsValue)
  extends SubscribingPublisherActor
  with ActorWithTicks
  with WithOccurrenceAccounting
  with NowProvider {

  implicit val ec = context.dispatcher
  val occurrenceCondition = (props ~> 'occurrenceCondition | "None").toLowerCase match {
    case "less than" => OccurrenceConditionLessThan()
    case "more than" => OccurrenceConditionMoreThan()
    case "exactly" => OccurrenceConditionExactly()
    case _ => OccurrenceConditionNone()
  }
  val occurrenceCount = props +> 'occurrenceCount | -1
  val occurrenceWatchPeriodSec = (props +> 'occurrenceWatchPeriodSec | 1) match {
    case x if x < 1 => 1
    case x => x
  }
  val simpleCondition = SimpleCondition.conditionOrAlwaysTrue(props ~> 'simpleCondition).get
  val title = props ~> 'title
  val body = props ~> 'body
  val icon = props ~> 'icon
  val expirySec = props +> 'expirySec
  val correlationIdTemplate = props ~> 'correlationIdTemplate
  val conflationKeyTemplate = props ~> 'conflationKeyTemplate
  val timestampSource = props ~> 'timestampSource
  val signalSubclass = props ~> 'signalSubclass
  val throttlingWindow = props +> 'throttlingWindow
  val throttlingAllowance = props +> 'throttlingAllowance
  val level = SignalLevel.fromString(props ~> 'level | "Very low")
  val (transactionDemarcation, transactionStatus) = (props ~> 'transactionDemarcation | "None").toLowerCase match {
    case "start" => (Some("start"), Some("open"))
    case "success" => (Some("success"), Some("closed"))
    case "failure" => (Some("failure"), Some("closed"))
    case "none" => (None, None)
  }
  val buckets = mutable.Map[BucketKey, Int]()
  private val maxInFlight = props +> 'buffer | 1000
  var sequenceCounter: Long = 0


  override def occurrenceAccountingPeriodSec: Int = throttlingWindow match {
    case Some(x) if x > 0 => x
    case _ => 1
  }


  def bucketIdByTs(ts: Long): Long = ts / 1000

  def currentBucket: Long = bucketIdByTs(now)

  def cleanup() = {
    val validBucketId = currentBucket - occurrenceWatchPeriodSec + 1
    buckets.collect { case (k, v) if k.bucketId < validBucketId => (k, v)} foreach {
      case (k, v) =>
        buckets.remove(k)
    }
  }

  def countSignals(cId: Option[String]) = buckets.foldLeft(0) { (count, mapEntry) =>
    mapEntry match {
      case (bucketId, bucketCount) if bucketId.correlationId == cId => count + bucketCount
      case _ => count
    }
  }


  override def becomeActive(): Unit = {
    resetCounters()
    super.becomeActive()
  }

  def accountSignal(s: Signal) = {
    logger.debug(s"Candidate signal $s")
    cleanup()
    val bucketId = bucketIdByTs(s.ts)
    val validBucket = currentBucket - occurrenceWatchPeriodSec + 1
    if (bucketId >= validBucket) {
      val key = BucketKey(bucketId, s.correlationId)
      buckets += key -> (buckets.getOrElse(key, 0) + 1)
    }
  }

  def eventToSignal(e: JsonFrame): Signal = {

    sequenceCounter = sequenceCounter + 1

    val eventId = e.event ~> 'eventId | "undefined"
    val signalId = eventId + ":" + sequenceCounter
    val ts = timestampSource.flatMap { tsSource => Tools.locateFieldValue(e, tsSource).asOpt[Long]} | now

    Signal(signalId, sequenceCounter, ts,
      eventId, level, signalClass, signalSubclass,
      conflationKeyTemplate.map(Tools.macroReplacement(e, _)),
      correlationIdTemplate.map(Tools.macroReplacement(e, _)),
      transactionDemarcation, transactionStatus,
      title.map(Tools.macroReplacement(e, _)),
      body.map(Tools.macroReplacement(e, _)),
      icon.map(Tools.macroReplacement(e, _)),
      expirySec.map(_ * 1000 + now))
  }

  def signalToEvent(s: Signal): JsonFrame = JsonFrame(Json.obj(
    "eventId" -> s.signalId,
    "eventSeq" -> s.sequenceId,
    DateInstructionConstants.default_targetFmtField -> DateTime.now().toString(DateInstructionConstants.default),
    DateInstructionConstants.default_targetTsField -> DateTime.now().getMillis,
    "transaction" -> (transactionDemarcation.map { demarc =>
      Json.obj(
        "demarcation" -> demarc,
        "status" -> JsString(transactionStatus.getOrElse("unknown"))
      )
    } | Json.obj()),
    "signal" -> Json.obj(
      "sourceEventId" -> s.eventId,
      "conflationKey" -> s.conflationKey,
      "correlationId" -> s.correlationId,
      "signalClass" -> s.signalClass,
      "signalSubclass" -> s.signalSubclass,
      "title" -> s.title,
      "body" -> s.body,
      "icon" -> s.icon,
      "expiryTs" -> s.expiryTs,
      "level" -> s.level.code,
      "time" -> s.ts,
      "time_fmt" -> new DateTime(s.ts).toString(DateInstructionConstants.default)
    )
  ), Map())


  def throttlingAllowed() = throttlingAllowance match {
    case Some(x) if x > 0 =>
      x > accountedOccurrencesCount
    case _ => true
  }


  override def execute(frame: JsonFrame): Option[Seq[JsonFrame]] = {
    if (simpleCondition.metFor(frame).isRight) {
      val signal = eventToSignal(frame)
      accountSignal(signal)
      if (throttlingAllowed() && occurrenceCondition.isMetFor(occurrenceCount, countSignals(signal.correlationId))) {
        mark(now)
        Some(List(frame, signalToEvent(signal)))
      } else {
        Some(List(frame))
      }
    } else Some(List(frame))
  }


  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    if (occurrenceCount == 0
      && isActive
      && isPipelineActive
      && millisTimeSinceStateChange > occurrenceWatchPeriodSec * 1000
      && countSignals(correlationIdTemplate.map { s => Tools.macroReplacement(JsonFrame(Json.obj(), Map()), s)}) == 0) {
      if (throttlingAllowed()) {
        mark(now)
        forwardToNext(signalToEvent(eventToSignal(JsonFrame(Json.obj("eventId" -> Utils.generateShortUUID, "ts" -> now), Map()))))
      }
    }
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int =
      pendingToDownstreamCount
  }


}

sealed trait OccurrenceCondition {
  def isMetFor(count: Int, currentCount: Long): Boolean
}

case class OccurrenceConditionNone() extends OccurrenceCondition {
  override def isMetFor(count: Int, currentCount: Long): Boolean = true
}

case class OccurrenceConditionMoreThan() extends OccurrenceCondition {
  override def isMetFor(count: Int, currentCount: Long): Boolean = count > -1 && currentCount > count
}

case class OccurrenceConditionLessThan() extends OccurrenceCondition {
  override def isMetFor(count: Int, currentCount: Long): Boolean = count > -1 && currentCount < count
}

case class OccurrenceConditionExactly() extends OccurrenceCondition {
  override def isMetFor(count: Int, currentCount: Long): Boolean = count > -1 && currentCount == count
}

private case class BucketKey(bucketId: Long, correlationId: Option[String])


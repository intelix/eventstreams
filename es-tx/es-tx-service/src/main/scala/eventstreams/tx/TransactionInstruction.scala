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

package eventstreams.tx

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.Tools._
import eventstreams._
import eventstreams.core.actors.{ActorWithTicks, StateChangeSysevents, StoppableSubscribingPublisherActor}
import eventstreams.instructions.Types._
import eventstreams.instructions.{DateInstructionConstants, InstructionConstants}
import play.api.libs.json._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scalaz.Scalaz._
import scalaz._

trait TransactionInstructionSysevents
  extends ComponentWithBaseSysevents
  with StateChangeSysevents {
  val TxInstructionInstance = 'TxInstructionInstance.trace

  val NewTransaction = 'NewTransaction.trace
  val TransactionStart = 'TransactionStart.trace
  val TransactionFinish = 'TransactionFinish.trace
  val TransactionCompleted = 'TransactionCompleted.trace
  val TransactionEvicted = 'TransactionEvicted.trace

  override def componentId: String = "Instruction.Tx"


}

trait TransactionInstructionConstants extends InstructionConstants with TransactionInstructionSysevents {
  val CfgFName = "name"
  val CfgFCorrelationIdTemplates = "correlationIdTemplates"
  val CfgFTxStartCondition = "txStartCondition"
  val CfgFTxEndSuccessCondition = "txEndSuccessCondition"
  val CfgFTxEndFailureCondition = "txEndFailureCondition"
  val CfgFMaxOpenTransactions = "maxOpenTransactions"
  val CfgFTimestampSource = "timestampSource"
  val CfgFTargetTxField = "targetTxField"
  val CfgFTargetTxElapsedMsField = "targetTxElapsedMsField"
  val CfgFTargetTxStatusField = "targetTxStatusField"
  val CfgFBuffer = "buffer"

  val CfgFEventIdTemplate = "eventIdTemplate"
  val CfgFStreamKeyTemplate = "streamKeyTemplate"
  val CfgFStreamSeedTemplate = "streamSeedTemplate"

  val CfgFTags = "tags"
  val CfgFTxTypeTemplate = "txTypeTemplate"


  val ValueStatusClosedSuccess = "closed_success"
  val ValueStatusClosedFailure = "closed_success"
  val ValueStatusIncomplete = "incomplete"

}

object TransactionInstructionConstants extends TransactionInstructionConstants

class TransactionInstruction extends BuilderFromConfig[InstructionType] with TransactionInstructionConstants {
  val configId = "tx"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      _ <- props ~> CfgFCorrelationIdTemplates
        orFail s"Invalid $configId instruction. Missing '$CfgFCorrelationIdTemplates' value. Contents: ${Json.stringify(props)}";
      _ <- props ~> CfgFTxStartCondition
        orFail s"Invalid $configId instruction. Missing '$CfgFTxStartCondition' value. Contents: ${Json.stringify(props)}";
      _ <- SimpleCondition.optionalCondition(props ~> CfgFTxEndSuccessCondition)
        orFail s"Invalid $configId instruction. Missing or invalid '$CfgFTxEndSuccessCondition' value. Contents: ${Json.stringify(props)}"
    ) yield TransactionInstructionActor.props(props)

}

private object TransactionInstructionActor {
  def props(config: JsValue) = Props(new TransactionInstructionActor(config))
}

private class TransactionInstructionActor(props: JsValue)
  extends StoppableSubscribingPublisherActor
  with ActorWithTicks
  with NowProvider
  with TransactionInstructionConstants
  with WithSyseventPublisher {

  implicit val ec = context.dispatcher

  val correlationIdTemplatesSeq: Seq[String] = (props ~> CfgFCorrelationIdTemplates | "").split(',').map(_.trim).filter(!_.isEmpty)

  val name = props ~> CfgFName | "default"
  val txStartCondition = SimpleCondition.optionalCondition(props ~> CfgFTxStartCondition)
  val txEndSuccessCondition = SimpleCondition.optionalCondition(props ~> CfgFTxEndSuccessCondition)
  val txEndFailureCondition = SimpleCondition.optionalCondition(props ~> CfgFTxEndFailureCondition)

  val eventIdTemplate = props ~> CfgFEventIdTemplate | "${first.eventId}:${last.eventId}"
  val streamKeyTemplate = props ~> CfgFStreamKeyTemplate | "${first.streamKey}"
  val streamSeedTemplate = props ~> CfgFStreamSeedTemplate | "0"
  val tags = (props ~> CfgFTags).map(_.split(',').map(_.trim).filterNot(_.isEmpty))
  val txTypeTemplate = props ~> CfgFTxTypeTemplate

  val maxOpenTransactions = props +> CfgFMaxOpenTransactions | 100000
  val timestampSource = props ~> CfgFTimestampSource | DateInstructionConstants.default_targetTsField

  val targetTxField = props ~> CfgFTargetTxField | "txId"
  val targetTxElapsedMsField = props ~> CfgFTargetTxElapsedMsField | "txElapsedMs"
  val targetTxStatusField = props ~> CfgFTargetTxStatusField | "txStatus"

  val maxInFlight = props +> CfgFBuffer | 1000

  val txById: mutable.Map[String, TxTimeline] = mutable.Map()
  var openTransactions: Int = 0

  override def onBecameActive(): Unit = {
    super.onBecameActive()
  }


  override def preStart(): Unit = {
    super.preStart()
    TxInstructionInstance >>> Seq(
      'Name -> name,
      'Templates -> correlationIdTemplatesSeq,
      'Start -> props ~> CfgFTxStartCondition,
      'EndSuccess -> props ~> CfgFTxEndSuccessCondition,
      'EndFailure -> props ~> CfgFTxEndFailureCondition,
      'MaxOpen -> maxOpenTransactions,
      'Buffer -> maxInFlight
    )
  }

  override def execute(value: EventFrame): Option[Seq[EventFrame]] =
    accountEvent(value) match {
      case Some(x) => Some(x :+ value)
      case None => Some(Seq(value))
    }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int =
      pendingToDownstreamCount
  }

  private def accountEvent(value: EventFrame): Option[Seq[EventFrame]] =
    for (
      eId <- value.eventId;
      txStartC <- txStartCondition;
      txEndSC <- txEndSuccessCondition;
      ts <- value ++> timestampSource;
      ids <- listOfTxIds(value);
      txEndFC = txEndFailureCondition | NeverTrueCondition();
      txB <- toDemarcType(ts, value, txStartC.metFor(value).isRight, txEndSC.metFor(value).isRight, txEndFC.metFor(value).isRight, ids)
    ) yield ids.flatMap(toEvents(_, txB)) ++ removeOldestIfMaxOpenExceeded()

  private def toDemarcType(ts: Long, evt: EventFrame, start: Boolean, finishWithSuccess: Boolean, finishWithFailure: Boolean, ids: Seq[String]): Option[TxDemarcation] =
    if (start) {
      TransactionStart >>('EventId -> evt.eventIdOrNA, 'TxIds -> ids.mkString(","))
      Some(TxStart(ts, evt))
    } else if (finishWithFailure) {
      TransactionFinish >>('EventId -> evt.eventIdOrNA, 'Success -> false, 'TxIds -> ids.mkString(","))
      Some(TxFinishFailure(ts, evt))
    } else if (finishWithSuccess) {
      TransactionFinish >>('EventId -> evt.eventIdOrNA, 'Success -> true, 'TxIds -> ids.mkString(","))
      Some(TxFinishSuccess(ts, evt))
    }
    else None

  private def toEvent(tx: Tx): EventFrame = {
    var e = EventFrame(
      targetTxField -> tx.id,
      targetTxElapsedMsField -> tx.elapsedMs,
      targetTxStatusField -> (if (tx.completed) {
        if (tx.isCompletedSuccessfully) ValueStatusClosedSuccess else ValueStatusClosedFailure
      } else ValueStatusIncomplete),
      "first" -> (tx.start.map(_.evt.asInstanceOf[EventData]) | EventDataValueNil()),
      "last" -> (tx.finish.map(_.evt.asInstanceOf[EventData]) | EventDataValueNil())
    )
    txTypeTemplate.foreach { t =>
      e = e + ('txType -> Tools.macroReplacement(e, t))
    }
    tags.foreach(_.foreach { t =>
      e = Tools.setValue("as", Tools.macroReplacement(e, t), "tags", e)
    })
    e
      .setEventId(Tools.macroReplacement(e, eventIdTemplate) match {
      case s if s.trim.isEmpty => UUIDTools.generateShortUUID
      case s => s
    })
      .setStreamKey(Tools.macroReplacement(e, streamKeyTemplate))
      .setStreamSeed(Tools.macroReplacement(e, streamSeedTemplate))
  }

  private def toEvents(id: String, txB: TxDemarcation): Seq[EventFrame] =
    txById.get(id) match {
      case None =>
        txById += (id -> (new TxTimeline() + (new Tx(id) + txB)))
        Seq()
      case Some(tl) =>
        tl ? txB match {
          case None =>
            tl + (new Tx(id) + txB)
            Seq()
          case Some(tx) => tx + txB match {
            case t if t.completed =>
              if ((tl - t).empty) txById -= id
              TransactionCompleted >>(
                'TxId -> id,
                'EventIdStart -> (tx.start.map(_.evt.eventIdOrNA) | "N/A"),
                'EventIdFinish -> (tx.finish.map(_.evt.eventIdOrNA) | "N/A"),
                'Success -> tx.isCompletedSuccessfully,
                'OpenTxCount -> openTransactions)
              Seq(toEvent(t))
            case _ => Seq()
          }
        }
    }

  private def removeOldestIfMaxOpenExceeded(): Seq[EventFrame] =
    if (openTransactions > maxOpenTransactions)
      txById.foldLeft[Option[(String, TxTimeline, Tx)]](None) {
        case (None, (k, v)) => Some((k, v, v.oldest))
        case (Some((bk, btl, btx)), (k, v)) if v.oldest.olderThan(btx) => Some((k, v, v.oldest))
        case (x, _) => x
      } match {
        case Some((k, tl, tx)) =>
          if ((tl - tx).empty) txById -= k
          TransactionEvicted >>(
            'TxId -> tx.id,
            'EventIdStart -> (tx.start.map(_.evt.eventIdOrNA) | "N/A"),
            'EventIdFinish -> (tx.finish.map(_.evt.eventIdOrNA) | "N/A"),
            'OpenTxCount -> openTransactions)
          Seq(toEvent(tx))
        case None => Seq()
      }
    else Seq()

  private def listOfTxIds(value: EventFrame): Option[Seq[String]] =
    correlationIdTemplatesSeq.map(Tools.macroReplacement(value, _)).map(_.trim).filter(!_.isEmpty) match {
      case Nil => None
      case x => Some(x)
    }

  sealed trait TxDemarcation {
    def ts: Long

    def evt: EventFrame
  }

  class Tx(val id: String) {

    var start: Option[TxDemarcation] = None
    var finish: Option[TxDemarcation] = None

    def +(d: TxDemarcation): Tx = {
      d match {
        case x: TxStart => start = Some(x)
        case x => finish = Some(x)
      }
      this
    }

    def elapsedMs: Long = if (!completed) -1 else finish.get.ts - start.get.ts

    def completed: Boolean = start.isDefined && finish.isDefined

    def olderThan(tx: Tx): Boolean = getOldestTs < tx.getOldestTs

    def isCompletedSuccessfully: Boolean = finish match {
      case Some(x: TxFinishSuccess) => completed
      case _ => false
    }

    private def getOldestTs = (start.map(_.ts).toList ++ finish.map(_.ts).toList).min
  }

  class TxTimeline {
    private val list: mutable.ListBuffer[Tx] = ListBuffer()

    def +(tx: Tx): TxTimeline = {
      openTransactions += 1
      NewTransaction >>('TxId -> tx.id, 'OpenTxCount -> openTransactions)
      list.indexWhere(_.olderThan(tx)) match {
        case i if i < 0 => list.append(tx)
        case i => list.insert(i, tx)
      }
      this
    }

    def -(tx: Tx): TxTimeline = {
      openTransactions -= 1
      list -= tx
      this
    }

    def ?(d: TxDemarcation): Option[Tx] =
      d match {
        case x: TxStart =>
          list.reverseIterator.collectFirst {
            case tx if tx.start.isEmpty && (tx.finish.map(_.ts >= x.ts) | false) => tx
          }
        case x =>
          list.collectFirst {
            case tx if tx.finish.isEmpty && (tx.start.map(_.ts <= x.ts) | false) => tx
          }
      }

    def empty: Boolean = list.isEmpty

    def oldest: Tx = list.last
  }

  case class TxStart(ts: Long, evt: EventFrame) extends TxDemarcation

  case class TxFinishSuccess(ts: Long, evt: EventFrame) extends TxDemarcation

  case class TxFinishFailure(ts: Long, evt: EventFrame) extends TxDemarcation

}

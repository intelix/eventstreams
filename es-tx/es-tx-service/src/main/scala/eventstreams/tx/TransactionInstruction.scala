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

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors.{ActorWithTicks, StoppableSubscribingPublisherActor}
import eventstreams.instructions.Types._
import eventstreams.instructions.{DateInstructionConstants, InstructionConstants}
import play.api.libs.json._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scalaz.Scalaz._
import scalaz._

trait TransactionInstructionSysevents extends ComponentWithBaseSysevents {
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
  val CfgFBuffer = "buffer"
}

object TransactionInstructionConstants extends TransactionInstructionConstants

class TransactionInstruction extends BuilderFromConfig[InstructionType] with TransactionInstructionConstants {
  val configId = "tx"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      _ <- props ~> CfgFCorrelationIdTemplates
        \/> Fail(s"Invalid $configId instruction. Missing '$CfgFCorrelationIdTemplates' value. Contents: ${Json.stringify(props)}");
      _ <- props ~> CfgFTxStartCondition
        \/> Fail(s"Invalid $configId instruction. Missing '$CfgFTxStartCondition' value. Contents: ${Json.stringify(props)}");
      _ <- SimpleCondition.optionalCondition(props ~> CfgFTxEndSuccessCondition)
        \/> Fail(s"Invalid $configId instruction. Missing or invalid '$CfgFTxEndSuccessCondition' value. Contents: ${Json.stringify(props)}")
    ) yield TransactionInstructionActor.props(props)

}

private object TransactionInstructionActor {
  def props(config: JsValue) = Props(new TransactionInstructionActor(config))
}

private class TransactionInstructionActor(props: JsValue)
  extends StoppableSubscribingPublisherActor
  with ActorWithTicks
  with NowProvider
  with TransactionInstructionSysevents
  with WithSyseventPublisher
  with TransactionInstructionConstants {

  implicit val ec = context.dispatcher

  val correlationIdTemplatesSeq: Seq[String] = (props ~> CfgFCorrelationIdTemplates | "").split(',').map(_.trim).filter(!_.isEmpty)

  val name = props ~> CfgFName | "default"
  val txStartCondition = SimpleCondition.optionalCondition(props ~> CfgFTxStartCondition)
  val txEndSuccessCondition = SimpleCondition.optionalCondition(props ~> CfgFTxEndSuccessCondition)
  val txEndFailureCondition = SimpleCondition.optionalCondition(props ~> CfgFTxEndFailureCondition)

  val maxOpenTransactions = props +> CfgFMaxOpenTransactions | 100000
  val timestampSource = props ~> CfgFTimestampSource | DateInstructionConstants.default_targetTsField

  val targetTxField = props ~> CfgFTargetTxField | "transactionId"
  val maxInFlight = props +> CfgFBuffer | 1000

  val txById: mutable.Map[String, TxTimeline] = mutable.Map()
  var openTransactions: Int = 0

  class Tx {

    var start: Option[TxDemarcation] = None
    var finish: Option[TxDemarcation] = None

    def +(d: TxDemarcation): Tx = {
      d match {
        case x: TxStart => start = Some(x)
        case x => finish = Some(x)
      }
      this
    }

    def completed: Boolean = start.isDefined && finish.isDefined

    def olderThan(tx: Tx): Boolean = getOldestTs < tx.getOldestTs

    private def getOldestTs = (start.map(_.ts).toList ++ finish.map(_.ts).toList).min
  }

  class TxTimeline {
    private val list: mutable.ListBuffer[Tx] = ListBuffer()

    def +(tx: Tx): TxTimeline = {
      openTransactions += 1
      list.insert(list.indexWhere(_.olderThan(tx)), tx)
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

  sealed trait TxDemarcation {
    def ts: Long

    def evt: EventFrame
  }

  case class TxStart(ts: Long, evt: EventFrame) extends TxDemarcation

  case class TxFinishSuccess(ts: Long, evt: EventFrame) extends TxDemarcation

  case class TxFinishFailure(ts: Long, evt: EventFrame) extends TxDemarcation

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int =
      pendingToDownstreamCount
  }

  override def onBecameActive(): Unit = {
    super.onBecameActive()
  }


  private def accountEvent(value: EventFrame): Option[Seq[EventFrame]] =
    for (
      txStartC <- txStartCondition;
      txEndSC <- txEndSuccessCondition;
      ts <- value ++> timestampSource;
      ids <- listOfTxIds(value);
      txEndFC = txEndFailureCondition | NeverTrueCondition();
      txB <- toBoundaryType(ts, value, txStartC.metFor(value).isRight, txEndSC.metFor(value).isRight, txEndFC.metFor(value).isRight)
    ) yield ids.flatMap(toEvents(_, txB)) ++ removeOldestIfMaxOpenExceeded()

  private def toBoundaryType(ts: Long, evt: EventFrame, start: Boolean, finishWithSuccess: Boolean, finishWithFailure: Boolean): Option[TxDemarcation] =
    if (start) Some(TxStart(ts, evt))
    else if (finishWithFailure) Some(TxFinishFailure(ts, evt))
    else if (finishWithSuccess) Some(TxFinishSuccess(ts, evt))
    else None

  private def toEvent(tx: Tx): EventFrame = 
    EventFrame(
      "start" -> (tx.start.map(_.evt.asInstanceOf[EventData]) | EventDataValueNil()),
      "finish" -> (tx.finish.map(_.evt.asInstanceOf[EventData]) | EventDataValueNil())
    )

  private def toEvents(id: String, txB: TxDemarcation): Seq[EventFrame] =
    txById.get(id) match {
      case None =>
        txById += (id -> (new TxTimeline() + (new Tx() + txB)))
        Seq()
      case Some(tl) =>
        tl ? txB match {
          case None =>
            tl + (new Tx() + txB)
            Seq()
          case Some(tx) => tx + txB match {
            case t if t.completed =>
              if ((tl - t).empty) txById -= id
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
          Seq(toEvent(tx))
        case None => Seq()
      }
    else Seq()

  private def listOfTxIds(value: EventFrame): Option[Seq[String]] =
    correlationIdTemplatesSeq.map(Tools.macroReplacement(value, _)).map(_.trim).filter(!_.isEmpty) match {
      case Nil => None
      case x => Some(x)
    }

  override def execute(value: EventFrame): Option[Seq[EventFrame]] =
    accountEvent(value) match {
      case Some(x) => Some(x :+ value)
      case None => Some(Seq(value))
    }
}

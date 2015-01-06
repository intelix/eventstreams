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

package eventstreams.engine.gates

import java.util.Calendar

import akka.actor.{ActorRef, Props}
import akka.agent.Agent
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import com.typesafe.config.Config
import eventstreams.core.actors.{ActorObjWithConfig, ActorWithComposableBehavior, ActorWithTicks}
import eventstreams.core.agent.core.{AcknowledgeAsProcessed, Acknowledgeable}
import eventstreams.core.{JsonFrame, NowProvider}
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Success}

object RetentionManagerActor extends ActorObjWithConfig {
  override def props(implicit config: Config): Props = Props(new RetentionManagerActor(config))

  override def id: String = "storage"
}

case class ScheduleStorage(ref: ActorRef, index: String, etype: String, id: String, v: JsValue)

case class InitiateReplay(ref: ActorRef, index: String, etype: String, limit: Int)

case class GetRetainedCount(ref: ActorRef, index: String, etype: String)

case class RetainedCount(count: Long)

case class ReplayStart()

case class ReplayEnd()

case class ReplayFailed(error: String)

case class ReplayedEvent(correlationId: Long, msg: String)

case class ClientRef(scrollId: String, ref: ActorRef)

private case class BatchSuccessful(bulkSize: Int)

private case class BatchFailed()

class RetentionManagerActor(config: Config) extends ActorWithComposableBehavior with NowProvider with ActorWithTicks {

  implicit val ec = context.dispatcher
  val bulkSize = 100
  private val host = config.getString("ehub.gates.retention.elastic.host")
  private val cluster = config.getString("ehub.gates.retention.elastic.cluster")
  private val port = config.getInt("ehub.gates.retention.elastic.port")
  private val cal = Calendar.getInstance()
  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build()
  private val queue = collection.mutable.Queue[JsonFrame]()
  private val clientAgent = Agent[Option[ElasticClient]](Some(ElasticClient.remote(settings, (host, port))))
  private var insertSequence = now
  private var lastDelivery: Long = 0

  private var batchCounter = 0
  private var runningBatch: Option[List[ScheduleStorage]] = None

  private var scheduledForDelivery = Map[String, ScheduleStorage]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def withClient(f: ElasticClient => Unit) = clientAgent.get().foreach(f)

  def deliverIfPossible() =
    if (runningBatch.isEmpty && ((now - lastDelivery > 500 && scheduledForDelivery.size > 0) || scheduledForDelivery.size >= bulkSize)) {
      deliverAll(scheduledForDelivery.take(bulkSize).values.toList)
      scheduledForDelivery = scheduledForDelivery.drop(bulkSize)
      lastDelivery = now
    }


  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    deliverIfPossible()
  }

  def enqueue(s: ScheduleStorage, enforce: Boolean = false) = {
    val key = s.id + "::" + s.index + "::" + s.etype
    (scheduledForDelivery.get(key) match {
      case Some(existing) => Some(existing.copy(v = existing.v.as[JsObject] ++ s.v.as[JsObject]))
      case None if !enforce && scheduledForDelivery.size > 1000 => None
      case None => Some(s)
    }) match {
      case Some(x) =>
        scheduledForDelivery = scheduledForDelivery + (key -> x)
        deliverIfPossible()
        true
      case None =>
        false
    }
  }

  def deliverAll(bunch: List[ScheduleStorage]) = withClient { esclient =>

    batchCounter = batchCounter + 1
    runningBatch = Some(bunch)

    esclient.execute {
      bulk(
        bunch.map { next =>
          val targetIndex = next.index + "/" + next.etype
          val json = next.v.set(__ \ 'insertSequence -> JsNumber(insertSequence))
          insertSequence = insertSequence + 1
          logger.debug(s"ES Batch $batchCounter Delivering ${next.id} -> $targetIndex : $json to $clientAgent  $settings at $host:$port  into $cluster")
          update(next.id) in targetIndex docAsUpsert true doc StringDocumentSource(Json.stringify(json))
        }: _*
      )
    } onComplete {
      case Success(_) =>
        logger.debug(s"ES Batch $batchCounter Delivered count: ${bunch.size}")
        self ! BatchSuccessful(bunch.size)
      case Failure(fail) =>
        logger.debug(s"ES Batch $batchCounter Failed to deliver", fail)
        self ! BatchFailed()
    }

  }

  def retrieveRetainedCount(m: GetRetainedCount) = withClient { esclient =>
    val idx = if (m.etype.isEmpty) m.index else m.index + "/" + m.etype

    esclient.execute {
      count from idx
    } onComplete {
      case Success(r) =>
        val count = r.getCount
        logger.info(s"Count $idx: $count")
        m.ref ! RetainedCount(count)
      case Failure(fail) =>
        logger.warn(s"Unable to retrieve count for $idx")
    }
  }

  private def handler: Receive = {
    case m: Acknowledgeable[_] => m.msg match {
      case s: ScheduleStorage => if (enqueue(s)) sender() ! AcknowledgeAsProcessed(m.id)
      case other =>
        logger.debug(s"Unsupported request type: $other")
        sender() ! AcknowledgeAsProcessed(m.id)
    }

    case m: InitiateReplay => context.actorOf(ReplayWorkerActor.props(clientAgent, m))
    case m: GetRetainedCount => retrieveRetainedCount(m)
    case BatchSuccessful(_) =>
      runningBatch = None
    case BatchFailed() =>
      logger.debug(s"Rescheduling all failed entries")
      val temp = scheduledForDelivery.values
      scheduledForDelivery = Map()
      runningBatch.foreach(_.foreach(enqueue(_, enforce = true)))
      temp.foreach(enqueue(_, enforce = true))
      runningBatch = None
  }
}


object ReplayWorkerActor {
  def props(clientAgent: Agent[Option[ElasticClient]], m: InitiateReplay) = Props(new ReplayWorkerActor(clientAgent, m))
}

class ReplayWorkerActor(clientAgent: Agent[Option[ElasticClient]], m: InitiateReplay)
  extends ActorWithComposableBehavior
  with ActorWithTicks
  with NowProvider {

  implicit val ec = context.dispatcher
  var pending = List[ReplayedEvent]()
  var counter: Long = 0
  var scrollId: Option[String] = None
  var scrollSessionStartedAt: Option[Long] = None
  var scrollSessionTimeout = 1.minute.toMillis


  logger.debug(s"Initiating replay on request: $m")

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def processTick(): Unit = {
    super.processTick()
    scrollSessionStartedAt match {
      case Some(t) if now - t > scrollSessionTimeout => finishWithFailure("Replay session timeout")
      case _ => ()
    }
  }

  def sendHead() = m.ref ! pending.head

  def newList(list: List[ReplayedEvent]) =
    if (list.isEmpty) {
      finishWithSuccess()
    } else {
      pending = list
      sendHead()
    }


  def moreResults() = clientAgent.get() match {
    case Some(esclient) =>
      val scId = scrollId.get
      logger.debug(s"Continuing querying $scId")
      esclient.searchScroll(scId, "1m") onComplete {
        case Success(r) =>
          scrollId = Some(r.getScrollId)
          scrollSessionStartedAt = Some(now)
          val list = r.getHits.getHits.map { h =>
            counter = counter + 1
            ReplayedEvent(counter, h.getSourceAsString)
          }.toList
          logger.debug(s"This time , total hist: ${r.getHits.getTotalHits} in this block: ${list.length}")
          newList(list)

        case Failure(fail) =>
          finishWithFailure("", Some(fail))
      }


    case None =>
      finishWithFailure("Elasticsearch is not available at the moment. Please try again")
  }

  def acknowledgeAndContinueReplaying(correlationId: Long) =
    if (pending.exists(_.correlationId == correlationId)) {
      pending = pending.filter(_.correlationId != correlationId)
      if (pending.isEmpty) {
        moreResults()
      } else {
        sendHead()
      }
    }

  def initiate() = clientAgent.get() match {
    case Some(esclient) =>
      m.ref ! ReplayStart()

      val idx = m.index + "/" + m.etype

      esclient.execute {
        search in idx sort(by field "ts", by field "insertSequence") scroll "1m" limit m.limit
      } onComplete {
        case Success(r) =>
          scrollId = Some(r.getScrollId)
          scrollSessionStartedAt = Some(now)
          val list = r.getHits.getHits.map { h =>
            counter = counter + 1
            ReplayedEvent(counter, h.getSourceAsString)
          }.toList
          logger.debug(s"First time , total hist: ${r.getHits.getTotalHits} in this block: ${list.length}")
          newList(list)

        case Failure(fail) =>
          finishWithFailure("", Some(fail))
      }
    case None =>
      finishWithFailure("Elasticsearch is not available at the moment. Please try again")
  }

  def finishWithSuccess() = {
    logger.debug("Replay successfully finished")
    m.ref ! ReplayEnd()
    context.stop(self)
  }

  def finishWithFailure(msg: String, error: Option[Throwable] = None) = {
    error match {
      case Some(t) => logger.debug(s"Failed: " + msg, t)
      case None => logger.debug(s"Failed: " + msg)
    }
    m.ref ! ReplayFailed("Error: " + msg)
    context.stop(self)
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    initiate()
  }

  def handler: Receive = {
    case AcknowledgeAsProcessed(id) => acknowledgeAndContinueReplaying(id)
  }
}


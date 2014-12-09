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

import java.util.Calendar

import agent.shared.AcknowledgeAsProcessed
import akka.actor.{ActorRef, Props}
import akka.agent.Agent
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import com.typesafe.config.Config
import common.{NowProvider, JsonFrame}
import common.actors.{ActorObjWithConfig, ActorWithComposableBehavior}
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.util.{Failure, Success}

object RetentionManagerActor extends ActorObjWithConfig {
  override def props(implicit config: Config): Props = Props(new RetentionManagerActor(config))

  override def id: String = "storage"
}

case class ScheduleStorage(ref: ActorRef, correlationId: Long, index: String, etype: String, id: String, v: JsValue)

case class MessageStored(correlationId: Long)

case class InitiateReplay(ref: ActorRef, index: String, etype: String, limit: Int)

case class ReplayStart()

case class ReplayEnd()

case class ReplayFailed(error: String)

case class ReplayedEvent(correlationId: Long, msg: String)

case class ClientRef(scrollId: String, ref: ActorRef)

class RetentionManagerActor(config: Config) extends ActorWithComposableBehavior with NowProvider {

  implicit val ec = context.dispatcher

  private val host = config.getString("ehub.gates.retention.elastic.host")
  private val cluster = config.getString("ehub.gates.retention.elastic.cluster")
  private val port = config.getInt("ehub.gates.retention.elastic.port")
  private val cal = Calendar.getInstance()

  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build()
  private val queue = collection.mutable.Queue[JsonFrame]()
  private val clientAgent = Agent[Option[ElasticClient]](Some(ElasticClient.remote(settings, (host, port))))


  private var insertSequence = now

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def withClient(f: ElasticClient => Unit) = clientAgent.get().foreach(f)

  def enqueue(m: ScheduleStorage) = withClient { esclient =>
    val targetIndex = m.index + "/" + m.etype

    val json = m.v.set(__ \ 'insertSequence -> JsNumber(insertSequence))

    insertSequence = insertSequence + 1

    esclient.execute {
      logger.debug(s"ES Delivering ${m.correlationId} -> $targetIndex : $json to $clientAgent  $settings at $host:$port  into $cluster")
      index into targetIndex id m.id doc StringDocumentSource(Json.stringify(json))
    } onComplete {
      case Success(_) =>
        logger.debug(s"ES Delivered ${m.correlationId}")
        m.ref ! MessageStored(m.correlationId)
      case Failure(fail) => logger.debug(s"ES Failed to deliver ${m.correlationId}", fail)
    }
  }

  private def handler: Receive = {
    case m: ScheduleStorage => enqueue(m)
    case m: InitiateReplay => context.actorOf(ReplayWorkerActor.props(clientAgent, m))
  }
}


object ReplayWorkerActor {
  def props(clientAgent: Agent[Option[ElasticClient]], m: InitiateReplay) = Props(new ReplayWorkerActor(clientAgent, m))
}

class ReplayWorkerActor(clientAgent: Agent[Option[ElasticClient]], m: InitiateReplay) extends ActorWithComposableBehavior {
  var pending = List[ReplayedEvent]()
  var counter = 0L
  var scrollId: Option[String] = None

  implicit val ec = context.dispatcher


  logger.debug(s"Initiating replay on request: $m")

  override def commonBehavior: Receive = handler orElse super.commonBehavior


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
          val scrollId = r.getScrollId
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
        search in idx sort(by field "ts", by field "insertSequence") scroll "1m" limit 2
      } onComplete {
        case Success(r) =>
          scrollId = Some(r.getScrollId)
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


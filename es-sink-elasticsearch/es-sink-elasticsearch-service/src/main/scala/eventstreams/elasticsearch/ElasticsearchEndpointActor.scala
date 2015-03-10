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

package eventstreams.elasticsearch

import java.util.Calendar

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.ActorRef
import akka.agent.Agent
import akka.cluster.Cluster
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors.{AutoAcknowledgingService, ActorObj, ActorWithComposableBehavior, ActorWithTicks}
import net.ceedubs.ficus.Ficus._
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.util.{Failure, Success}
import scalaz._
import Scalaz._


trait ElasticsearchEntpointSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Endpoint.Elasticsearch"
}

object ElasticsearchEndpointActor extends ActorObj {
  override def id: String = "epelasticsearch"
}


case class StoreInElasticsearch(ref: ActorRef, index: String, etype: String, id: String, v: String) extends WithID with CommMessage {
  override def entityId: Any = id
}

private case class BatchSuccessful(bulkSize: Int)

private case class BatchFailed()

class ElasticsearchEndpointActor(config: Config, c: Cluster)
  extends ActorWithComposableBehavior
  with ElasticsearchEntpointSysevents
  with AutoAcknowledgingService[StoreInElasticsearch]
  with WithSyseventPublisher
  with NowProvider with ActorWithTicks {

  val id = ElasticsearchEndpointActor.id

  implicit val ec = context.dispatcher
  
  private val host = config.getString("eventstreams.endpoints.elasticsearch.host")
  private val escluster = config.getString("eventstreams.endpoints.elasticsearch.cluster")
  private val port = config.getInt("eventstreams.endpoints.elasticsearch.port")
  private val bulkSize = config.as[Option[Int]]("eventstreams.endpoints.elasticsearch.batch-size") | 100 
  
  private val cal = Calendar.getInstance()
  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", escluster).build()
  private val queue = collection.mutable.Queue[EventFrame]()
  private val clientAgent = Agent[Option[ElasticClient]](Some(ElasticClient.remote(settings, host, port)))
  private var insertSequence = now
  private var lastDelivery: Long = 0

  private var batchCounter = 0
  private var runningBatch: Option[List[StoreInElasticsearch]] = None

  private var scheduledForDelivery = Map[String, StoreInElasticsearch]()

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

  def enqueue(s: StoreInElasticsearch, enforce: Boolean = false) = {
    val key = s.id + "::" + s.index + "::" + s.etype
    (scheduledForDelivery.get(key) match {
      case Some(existing) => Some(s)
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

  def deliverAll(bunch: List[StoreInElasticsearch]) = withClient { esclient =>

    batchCounter = batchCounter + 1
    runningBatch = Some(bunch)

    esclient.execute {
      bulk(
        bunch.map { next =>
          val targetIndex = next.index + "/" + next.etype
          val json = Json.parse(next.v).set(__ \ 'insertSequence -> JsNumber(insertSequence))
          insertSequence = insertSequence + 1
          logger.debug(s"ES Batch $batchCounter Delivering ${next.id} -> $targetIndex : $json to $clientAgent  $settings at $host:$port  into $escluster")
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

  private def handler: Receive = {
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

  override def canAccept(count: Int): Boolean = scheduledForDelivery.size < 1000

  override def onNext(e: StoreInElasticsearch): Unit = enqueue(e, enforce = true)
}




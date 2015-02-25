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

package eventstreams.sources.elasticsearch

import akka.actor.Props
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.JSONTools.configHelper
import eventstreams.core.actors._
import eventstreams.{EventAndCursor, EventFrameConverter}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._

trait ElasticsearchPublisherEvents extends ComponentWithBaseSysevents {
  val Built = 'Built.info

  val ScrollSearchInitiated = 'ScrollSearchInitiated.trace
  val ScrollSearchNext = 'ScrollSearchNext.trace
  val NextBatchReceived = 'NextBatchReceived.trace
  val ClientCreated = 'ClientCreated.trace

  override def componentId: String = "Eventsource.Elasticsearch"

}

object ElasticsearchPublisher {
  def props(streamKey: String, streamSeed: String, config: JsValue, cursor: Option[JsValue]): Props =
    Props(new ElasticsearchPublisher(streamKey, streamSeed, config, cursor))
}


class ElasticsearchPublisher(streamKey: String, val streamSeed: String, val props: JsValue, cursor: Option[JsValue])
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ElasticsearchPublisherEvents
  with WithSyseventPublisher
  with PullingPublisher[EventAndCursor] {

  implicit val sys = context.system
  implicit val ec = context.dispatcher


  val host = props ~> 'host | "localhost"
  val port = props +> 'port | 12345
  val index = props ~> 'index | "default"
  val escluster = props ~> 'cluster | "default"
  val etype = props ~> 'etype | ""
  val batchSize = props +> 'batchSize | 1000
  val limit = props ++> 'limit | -1
  val scrollKeepAlive = props ~> 'scrollKeepAlive | "10m"
  val settings = ImmutableSettings.settingsBuilder().put("cluster.name", escluster).build()

  var scrollId: Option[String] = cursor.flatMap(_ ~> 'scrollId)
  var pendingEntries: Seq[StreamElement] = Seq.empty

  var resultsCounterInSession: Long = 0

  var elasticClient: Option[ElasticClient] = None

  override def preStart(): Unit = {
    super.preStart()
    Built >>> Seq(
      'Host -> host,
      'Port -> port,
      'Index -> index,
      'Cluster -> escluster,
      'Batch -> batchSize,
      'Limit -> limit,
      'ScrollKeepAlive -> scrollKeepAlive)
  }


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq( 'StreamKey -> streamKey, 'StreamSeed -> streamSeed )

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def onBecameActive(): Unit = {
    createClient()
    resultsCounterInSession = 0
    self ! LoadFirst()
    super.onBecameActive()
  }

  override def onBecamePassive(): Unit = {
    destroyClient()
    super.onBecamePassive()
  }


  override def produceNext(maxCount: Int): Option[Seq[StreamElement]] =
    if (pendingEntries.nonEmpty) {
      val nextSeq = pendingEntries.take(maxCount)
      pendingEntries = pendingEntries.drop(maxCount)
      if (pendingEntries.isEmpty) self ! LoadMore()
      Some(nextSeq)
    } else None


  private def handler: Receive = {
    case LoadFirst() =>
      scrollId = None
      initiate()
    case LoadMore() => scrollId match {
      case None => ()
      case Some(scId) => moreResults(scId)
    }
    case NextBatch(hits, total) =>
      NextBatchReceived >>('Size -> hits.size, 'TotalHits -> total)
      if (hits.length == 0) {
        scrollId = None
        pendingEntries = pendingEntries :+ EndOfStream()
      } else {
        pendingEntries = pendingEntries ++ (limit match {
          case lim if lim > 0 && hits.size + resultsCounterInSession > lim =>
            val diff = (lim - resultsCounterInSession).toInt
            resultsCounterInSession = lim
            scrollId = None
            hits.take(diff).map(ScheduledEvent) :+ EndOfStream()
          case _ =>
            resultsCounterInSession += hits.size
            hits.map(ScheduledEvent)
        })
      }
      onDataAvailable()
    case ResultFailure(fail) =>
      Error >> ('Message -> fail)
      scrollId = None
      pendingEntries = pendingEntries :+ EndOfStreamWithError(fail)
      onDataAvailable()
  }

  private def destroyClient() = elasticClient = None

  private def createClient() = elasticClient = Some(ElasticClient.remote(settings, host, port))

  private def toCursor = scrollId.map { id => Json.obj("scrollId" -> id)}

  private def convertHits(f: Try[SearchResponse]) = f match {
    case Success(r) =>
      scrollId = Some(r.getScrollId)
      val c = toCursor
      val hits = r.getHits.getHits.map { h =>
        EventAndCursor(EventFrameConverter.fromJson(Json.parse(h.getSourceAsString)), c)
      }.toSeq
      self ! NextBatch(hits, r.getHits.getTotalHits)
    case Failure(fail) =>
      fail.printStackTrace()
      self ! ResultFailure(fail)
  }

  private def initiate() = elasticClient match {
    case Some(c) =>
      val idx = index + "/" + etype
      ScrollSearchInitiated >> ('Query -> idx)
      c.execute {
        search in idx sort(by field "ts", by field "insertSequence") scroll scrollKeepAlive limit batchSize
      } onComplete convertHits
    case None =>
      pushEndOfStreamWithError("Elasticsearch is not available at the moment. Please try again")
  }


  private def moreResults(scId: String) = elasticClient match {
    case Some(c) =>
      ScrollSearchNext >> ('ScrollId -> scId)
      c.searchScroll(scId, "10m") onComplete convertHits
    case None =>
      pushEndOfStreamWithError("Elasticsearch is not available at the moment. Please try again")
  }


}

private case class LoadFirst()

private case class LoadMore()

private case class NextBatch(hits: Seq[EventAndCursor], totalHits: Long)

private case class ResultFailure(t: Throwable)

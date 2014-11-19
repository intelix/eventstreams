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

package hq.flows.core

import agent.controller.flow.Tools._
import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import common.ToolExt.configHelper
import common.actors.{ActorWithTicks, SubscribingPublisherActor}
import common.{Fail, JsonFrame}
import hq.flows.core.Builder._
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.util.{Failure, Success}
import scalaz.Alpha.D
import scalaz.Scalaz._
import scalaz.\/

private[core] object ElasticsearchInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "es"

  override def build(props: JsValue, maybeData: Option[Condition]): \/[Fail, InstructionType] =
    for (
      index <- props ~> 'index \/> Fail(s"Invalid elasticsearch instruction configuration. Missing 'name' value. Contents: ${Json.stringify(props)}")
    ) yield GateInstructionActor.props(index, props)

}

case class DeliveryFailed(fail: Throwable)

case class DeliverySuccessful()

private object ElasticsearchInstructionActor {
  def props(index: String, config: JsValue) = Props(new ElasticsearchInstructionActor(index, config))
}

private class ElasticsearchInstructionActor(idx: String, config: JsValue)
  extends SubscribingPublisherActor
  with ActorWithTicks {

  implicit val ec = context.dispatcher

  private val maxInFlight = config +> 'buffer | 96
  private val branch = config ~> 'branch
  private val idSource = config ~> 'idSource | "id"
  private val host = config ~> 'host | "localhost"
  private val port = config +> 'port | 9300
  private val cluster = config ~> 'cluster | "elasticsearch"
  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build()
  private val queue = collection.mutable.Queue[JsonFrame]()
  private var client: Option[ElasticClient] = None

  private var deliveringNow: Option[JsonFrame] = None

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
  }


  override def becomeActive(): Unit = {
    client = Some(ElasticClient.remote(settings, (host, port)))
    logger.info(s"Elasticsearch link becoming active")
  }

  override def becomePassive(): Unit = {
    client.foreach(_.close())
    client = None
    logger.info(s"Elasticsearch link becoming passive")
  }

  override def execute(value: JsonFrame): Option[Seq[JsonFrame]] = {
    queue.enqueue(value)
    deliverIfPossible()
    None
  }

  override def processTick(): Unit = {
    super.processTick()
    deliverIfPossible()
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int =
      queue.size + pendingToDownstreamCount
  }

  @tailrec
  private def deliverIfPossible() : Unit =
    if (isPipelineActive && isActive && queue.size > 0 && deliveringNow.isEmpty && client.isDefined) {
      client.foreach { c =>
        val next = queue.head
        logger.debug(s"!>> Delivering to elastic - $next")

        deliveringNow = Some(next)

        val branchToPost = (for (
          b <- branch;
          v <- locateFieldValue(next, b).asOpt[JsValue]
        ) yield v) | next.event

        val id = locateFieldValue(next, idSource).asOpt[String]

        c.execute {
          index into idx doc StringDocumentSource(Json.stringify(branchToPost))
        } onComplete {
          case Success(_) => self ! DeliverySuccessful()
          case Failure(fail) => self ! DeliveryFailed(fail)
        }

      }
      deliverIfPossible()
    }

  private def handler: Receive = {
    case DeliverySuccessful() =>
      logger.debug("Successful delivery to elastic")
      deliveringNow.foreach(forwardToNext)
      deliveringNow = None
      queue.dequeue()
      deliverIfPossible()
    case DeliveryFailed(fail) =>
      logger.warn("Delivery to elastic failed", fail)
      deliveringNow = None
  }

}

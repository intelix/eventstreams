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

package eventstreams.engine.flows.core

import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors.{ActorWithTicks, StoppableSubscribingPublisherActor}
import eventstreams.core._
import Tools._
import Types._
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json.{JsString, JsValue, Json}

import scala.annotation.tailrec
import scala.util.{Failure, Success}
import scalaz.Scalaz._
import scalaz.\/

class ElasticsearchInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "es"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      index <- props ~> 'index \/> Fail(s"Invalid elasticsearch instruction configuration. Missing 'index' value. Contents: ${Json.stringify(props)}")
    ) yield ElasticsearchInstructionActor.props(index, props)

}

case class DeliveryFailed(fail: Throwable)

case class DeliverySuccessful()

private object ElasticsearchInstructionActor {
  def props(index: String, config: JsValue) = Props(new ElasticsearchInstructionActor(index, config))
}

private class ElasticsearchInstructionActor(idx: String, config: JsValue)
  extends StoppableSubscribingPublisherActor
  with ActorWithTicks {

  implicit val ec = context.dispatcher

  private val maxInFlight = config +> 'buffer | 1000
  private val branch = config ~> 'branch
  private val idSource = config ~> 'idSource | "id"
  private val host = config ~> 'host | "localhost"
  private val port = config +> 'port | 9300
  private val cluster = config ~> 'cluster | "elasticsearch"
  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build()
  private val queue = collection.mutable.Queue[JsonFrame]()
  private var client: Option[ElasticClient] = None
  private val condition = SimpleCondition.conditionOrAlwaysTrue(config ~> 'simpleCondition)

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
    // TODO log failed condition
    if (!condition.isDefined || condition.get.metFor(value).isRight) {
      queue.enqueue(value)
      deliverIfPossible()
      None
    } else Some(List(value))
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
    if (isComponentActive && isActive && queue.size > 0 && deliveringNow.isEmpty && client.isDefined) {
      client.foreach { c =>
        val next = queue.head

        deliveringNow = Some(next)

        val branchToPost = (for (
          b <- branch;
          v <- locateFieldValue(next, b).asOpt[JsValue]
        ) yield v) | next.event

        val targetId = locateFieldValue(next, idSource).asOpt[String]

        val targetIndex = macroReplacement(next, JsString(idx)).asOpt[String] | idx


        c.execute {
          targetId match {
            case None =>
              logger.debug(s"!>> Delivering to elastic - $next  -> index into $targetIndex doc $branchToPost")
              index into targetIndex doc StringDocumentSource(Json.stringify(branchToPost))
            case Some(x) =>
              logger.debug(s"!>> Delivering to elastic - $next  -> index into $targetIndex id $x doc $branchToPost")
              index into targetIndex id x doc StringDocumentSource(Json.stringify(branchToPost))
          }
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
      deliveringNow.foreach(forwardToFlow)
      deliveringNow = None
      queue.dequeue()
      deliverIfPossible()
    case DeliveryFailed(fail) =>
      logger.warn("Delivery to elastic failed", fail)
      deliveringNow = None
  }

}

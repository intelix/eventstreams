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
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import common.ToolExt.configHelper
import common.actors.{ActorWithTicks, SubscribingPublisherActor}
import common.{Fail, JsonFrame, NowProvider}
import hq.flows.core.Builder._
import org.influxdb.Client
import play.api.libs.json._

import scala.annotation.tailrec
import scala.util.{Failure, Success}
import scalaz.Scalaz._
import scalaz.\/

private[core] object InfluxInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "es"

  override def build(props: JsValue, maybeData: Option[Condition]): \/[Fail, InstructionType] =
    for (
      series <- props ~> 'series \/> Fail(s"Invalid influx instruction configuration. Missing 'series' value. Contents: ${Json.stringify(props)}")
    ) yield InfluxInstructionActor.props(series, props)

}


private object InfluxInstructionActor {
  def props(series: String, config: JsValue) = Props(new InfluxInstructionActor(series, config))
}

private class InfluxInstructionActor(series: String, config: JsValue)
  extends SubscribingPublisherActor
  with ActorWithTicks
  with NowProvider {

  implicit val ec = context.dispatcher

  private val maxInFlight = config +> 'buffer | 96

  private val aggregatorCount = config +> 'aggregatorCount | 30
  private val aggregatorPeriodMillis = config +> 'aggregatorPeriodMillis | 1000

  private val database = config ~> 'db | "series"

  private val timeSource = config ~> 'timeSource | "date_ts"
  private val columns = config ~> 'columns | "columns"
  private val points = config ~> 'points | "points"

  private val host = config ~> 'host | "localhost"
  private val port = config +> 'port | 8086

  private val user = config ~> 'user | "user"
  private val passw = config ~> 'password | ""

  private var queue = List[JsonFrame]()
  private var client: Option[Client] = Some(new Client(host = host + ":" + port, username = user, password = passw, database = database))

  private var deliveringNow: Option[Int] = None
  private var lastSent: Option[Long] = None

  override def preStart(): Unit = {
    super.preStart()
  }


  override def becomeActive(): Unit = {
    logger.info(s"Influx link becoming active")
  }

  override def becomePassive(): Unit = {
    client.foreach(_.close())
    client = None
    logger.info(s"Influx link becoming passive")
  }

  override def execute(value: JsonFrame): Option[Seq[JsonFrame]] = {
    queue = queue :+ value
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

  private def aggregatorCriteriaMet: Boolean =
    (now - lastSent.getOrElse(0L)) > aggregatorPeriodMillis || queue.size >= aggregatorCount

  private def toPoints(frame: JsonFrame): JsValue =
    Json.toJson(JsString(timeSource) +: points.split(",").map { pointSource =>
      locateFieldValue(frame, pointSource.trim).asOpt[JsValue] | JsNull
    }.toArray[JsValue])

  private def toColumns: JsValue =
    Json.toJson(JsString("time") +: columns.split(",").map { column =>
      JsString(column.trim())
    }.toArray[JsValue])


  @tailrec
  private def deliverIfPossible(): Unit =
    if (isPipelineActive && isActive && queue.size > 0 && deliveringNow.isEmpty && client.isDefined && aggregatorCriteriaMet) {
      client.foreach { c =>

        val mappedList = Json.toJson(queue.map(toPoints).toArray)
        deliveringNow

        val next = queue.head
        logger.debug(s"!>> Delivering to influx")

        deliveringNow = Some(queue.size)

        val s = Json.arr(
          Json.obj(
            "time" -> System.currentTimeMillis(),
            "name" -> series,
            "columns" -> toColumns,
            "points" -> mappedList
          ))

        c.writeSeries(s) match {
          case Some(err) =>
            logger.error(s"Delivery failed with error: $err")
          case None =>
            deliveringNow.foreach(queue.drop)
            lastSent = Some(now)
            deliveringNow = None
        }

      }
      deliverIfPossible()
    }


}

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

package eventstreams.influxdb

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.Props
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams._
import Tools.{configHelper, _}
import eventstreams.instructions.Types
import Types.InstructionType
import eventstreams.core._
import eventstreams.core.actors.{ActorWithTicks, StoppableSubscribingPublisherActor}
import org.influxdb.Client
import play.api.libs.json._

import scala.annotation.tailrec
import scalaz.Scalaz._
import scalaz.\/

trait InfluxDBInstructionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Instruction.InfluxDB"
}

class InfluxDBInstruction extends BuilderFromConfig[InstructionType] {
  val configId = "influx"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      series <- props ~> 'series \/> Fail(s"Invalid influx instruction configuration. Missing 'series' value. Contents: ${Json.stringify(props)}")
    ) yield InfluxDBInstructionActor.props(series, props)

}


private object InfluxDBInstructionActor {
  def props(series: String, config: JsValue) = Props(new InfluxDBInstructionActor(series, config))
}

private class InfluxDBInstructionActor(series: String, config: JsValue)
  extends StoppableSubscribingPublisherActor
  with ActorWithTicks
  with NowProvider 
  with InfluxDBInstructionSysevents
  with WithSyseventPublisher {

  implicit val ec = context.dispatcher

  private val maxInFlight = config +> 'buffer | 1000

  private val bulkSizeLimit = config +> 'bulkSizeLimit | 100
  private val bulkCollectionPeriodMillis = config +> 'bulkCollectionPeriodMillis | 1000

  private val database = config ~> 'db | "series"

  private val eventSeqSource = config ~> 'sequenceSource | "eventSeq"
  private val timeSource = config ~> 'timeSource | "date_ts"
  private val columns = config ~> 'columns | "value"
  private val points = config ~> 'points | "points"

  private val host = config ~> 'host | "localhost"
  private val port = config +> 'port | 8086

  private val user = config ~> 'user | "user"
  private val passw = config ~> 'password | ""

  private val condition = SimpleCondition.conditionOrAlwaysTrue(config ~> 'simpleCondition)

  private var queue = List[EventFrame]()
  private var client: Option[Client] = Some(new Client(host = host + ":" + port, username = user, password = passw, database = database))

  private var deliveringNow: Option[Int] = None
  private var lastSent: Option[Long] = None

  override def preStart(): Unit = {
    super.preStart()
  }


  override def onBecameActive(): Unit = {
    logger.info(s"Influx link becoming active")
  }

  override def onBecamePassive(): Unit = {
    client.foreach(_.close())
    client = None
    logger.info(s"Influx link becoming passive")
  }

  override def execute(value: EventFrame): Option[Seq[EventFrame]] = {
    // TODO log failed condition
    if (!condition.isDefined || condition.get.metFor(value).isRight) {
      queue = queue :+ value
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

  private def aggregatorCriteriaMet: Boolean =
    (now - lastSent.getOrElse(0.toLong)) > bulkCollectionPeriodMillis || queue.size >= bulkSizeLimit

  private def toPoints(frame: EventFrame): JsValue =
    Json.toJson((timeSource + "," + eventSeqSource + "," + points).split(",").map { field =>
      locateRawFieldValue(frame, field.trim, 0).asJson
    }.toArray[JsValue])

  private def toColumns: JsValue =
    Json.toJson(JsString("time") +: JsString("sequence_number") +: columns.split(",").map { column =>
      JsString(column.trim())
    }.toArray[JsValue])


  @tailrec
  private def deliverIfPossible(): Unit =
    if (isComponentActive && isActive && queue.size > 0 && deliveringNow.isEmpty && client.isDefined && aggregatorCriteriaMet) {
      client.foreach { c =>

        val pairs = queue.map { entry => (macroReplacement(entry, series), entry)}

        val uniqueSeries = pairs.collect { case (key, _) => key}.distinct.map { seriesName =>

          val relatedPoints = pairs.collect { case (key, frame) if key == seriesName => frame}.map(toPoints).toArray

          Json.obj(
            "name" -> seriesName,
            "columns" -> toColumns,
            "points" -> Json.toJson(relatedPoints)
          )
        }

        logger.debug(s"!>> Delivering to influx")

        deliveringNow = Some(queue.size)


        val combined = Json.toJson(uniqueSeries.toArray)

        c.writeSeriesWithTimePrecision(combined, "ms") match {
          case Some(err) =>
            logger.error(s"Delivery failed with error: $err")
          case None =>
            deliveringNow.foreach { count =>
              queue.take(count).foreach(pushSingleEventToStream)
              queue = queue.drop(count)
            }
            lastSent = Some(now)
            deliveringNow = None
        }

      }
      deliverIfPossible()
    }


}

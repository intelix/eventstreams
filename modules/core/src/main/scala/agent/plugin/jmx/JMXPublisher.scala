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

package agent.plugin.jmx

import agent.core.ProducedMessage
import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage.Request
import common.ToolExt.configHelper
import common.actors.{ActorWithComposableBehavior, ActorWithTicks, PipelineWithStatesActor, ShutdownablePublisherActor}
import fr.janalyse.jmx._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.math.BigDecimal
import scalaz.Scalaz._

object JMXPublisher {
  def props(config: JsValue): Props = Props(new JMXPublisher(config))
}


class JMXPublisher(val props: JsValue)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithTicks
  with ShutdownablePublisherActor[ProducedMessage] {

  implicit val sys = context.system


  val host = props ~> 'host | "localhost"
  val port = props +> 'port | 12345
  val intervalSec = props +> 'intervalSec | 15
  val searchPatterns = props ~> 'searchPatterns | ".*"
  val queue = mutable.Queue[JsValue]()

  val options = JMXOptions(host, port)
  val masks = searchPatterns.split(",").map { s =>
    s"(?i)${s.trim}".r
  }

  var lastQuery: Option[Long] = None

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"About to start JMX client")
  }


  override def postStop(): Unit = {
    super.postStop()
  }

  override def becomeActive(): Unit = {
    logger.info(s"JMX Becoming active ")
    super.becomeActive()
  }

  override def becomePassive(): Unit = {

    logger.info(s"JMX Becoming passive")
    super.becomePassive()
  }

  def handler: Receive = {
    case Request(n) =>
      logger.debug(s"Downstream requested $n messages")
      deliverIfPossible()
  }

  override def processTick(): Unit = {
    super.processTick()
    lastQuery = lastQuery match {
      case Some(x) if now - x < intervalSec * 1000 => lastQuery
      case _ =>
        load()
        Some(now)
    }
    deliverIfPossible()
  }


  @tailrec
  private def deliverIfPossible(): Unit = {

    logger.debug(s"!>>>>> jmx publisher - ${isActive} && ${isPipelineActive} && ${totalDemand}  && ${queue.size}")

    if (isActive && isPipelineActive && totalDemand > 0 && queue.size > 0) {
      onNext(ProducedMessage(queue.dequeue(), None))
      deliverIfPossible()
    }
  }


  private def load() = {
    if (isActive && isPipelineActive) {
      logger.debug(s"Loading...")
      JMX.once(options) { jmx =>
        for {
          mbean <- jmx.mbeans
          attr <- mbean.attributes
          value <- mbean.getString(attr)
        } {

          val found = List(mbean.name, attr.name, value).exists { item =>
            masks.exists {
              _.findFirstIn(item).isDefined
            }
          }
          if (masks.isEmpty || found) {
            if (attr.name != "ObjectName")
              attr match {
                case n: RichDoubleAttribute => mbean.getDouble(n).map { d =>
                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "mbean" -> mbean.name,
                      "ts" -> now,
                      "type" -> "double",
                      "type2" -> "n",
                      "keys" -> "value_num",
                      "attr" -> attr.name,
                      "series" -> "value",
                      "value_num" -> d
                    )
                  ))
                }
                case n: RichFloatAttribute => mbean.getDouble(n).map { d =>
                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "mbean" -> mbean.name,
                      "ts" -> now,
                      "type" -> "float",
                      "type2" -> "n",
                      "keys" -> "value_num",
                      "attr" -> attr.name,
                      "series" -> "value",
                      "value_num" -> d
                    )
                  ))
                }
                case n: RichNumberAttribute => mbean.getString(n).map { s =>
                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "mbean" -> mbean.name,
                      "ts" -> now,
                      "type" -> "long",
                      "type2" -> "n",
                      "keys" -> "value_num",
                      "attr" -> attr.name,
                      "series" -> "value",
                      "value_num" -> BigDecimal(s)
                    )
                  ))
                }
                case n: RichBooleanAttribute => mbean.getString(n).map { s =>
                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "mbean" -> mbean.name,
                      "ts" -> now,
                      "type" -> "boolean",
                      "type2" -> "b",
                      "attr" -> attr.name,
                      "value_bool" -> s.toBoolean
                    )
                  ))
                }
                case n: RichCompositeDataAttribute => mbean.getComposite(n).map { s =>

                  var numJson:JsValue = Json.obj()
                  var strJson:JsValue = Json.obj()

                  val list = s.toList

                  val numeric = list.foreach {
                    case (k,v) => v match {
                      case x if x.isInstanceOf[Long] => numJson = numJson.set(__ \ k -> JsNumber(x.asInstanceOf[Long]))
                      case x if x.isInstanceOf[Float] => numJson = numJson.set(__ \ k -> JsNumber(x.asInstanceOf[Float].toDouble))
                      case x if x.isInstanceOf[Int] => numJson = numJson.set(__ \ k -> JsNumber(x.asInstanceOf[Int]))
                      case x if x.isInstanceOf[Double] => numJson = numJson.set(__ \ k -> JsNumber(x.asInstanceOf[Double]))
                      case x if x.isInstanceOf[BigDecimal] => numJson = numJson.set(__ \ k -> JsNumber(x.asInstanceOf[BigDecimal]))
                      case _ => ()
                    }
                  }

                  val other = list.foreach {
                    case (k,v) => v match {
                      case x if x.isInstanceOf[String] => strJson = strJson.set(__ \ k -> JsString(x.asInstanceOf[String]))
                      case x if x.isInstanceOf[Boolean] => strJson = strJson.set(__ \ k -> JsBoolean(x.asInstanceOf[Boolean]))
                      case x => ()
                    }
                  }

                  val keys = list.map("jmx.values_num."+_._1)

                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "ts" -> now,
                      "mbean" -> mbean.name,
                      "type" -> "composite",
                      "type2" -> "c",
                      "attr" -> attr.name,
                      "keys" -> keys,
                      "values_num" -> numJson,
                      "values_str" -> strJson
                    )
                  ))
                }
                case n => mbean.getString(n).map { s =>
                  queue.enqueue(Json.obj(
                    "jmx" -> Json.obj(
                      "ts" -> now,
                      "mbean" -> mbean.name,
                      "type" -> "string",
                      "type2" -> "s",
                      "keys" -> "value_str",
                      "attr" -> attr.name,
                      "value_str" -> s
                    )
                  ))
                }
              }

          }
        }
      }
      logger.debug(s"Loaded...")

    }
  }


}
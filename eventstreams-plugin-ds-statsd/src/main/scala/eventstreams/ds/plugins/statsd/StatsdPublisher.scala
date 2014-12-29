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

package eventstreams.ds.plugins.statsd

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Udp.Unbind
import akka.io.{IO, Udp}
import akka.stream.actor.ActorPublisherMessage.Request
import akka.util.ByteString
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithTicks, PipelineWithStatesActor, StoppablePublisherActor}
import eventstreams.core.agent.core.ProducedMessage
import play.api.libs.json.{JsNumber, JsString, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Try
import scalaz.Scalaz._
import scalaz._

object StatsdPublisher {
  def props(config: JsValue): Props = Props(new StatsdPublisher(config))
}


class StatsdPublisher(val props: JsValue)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with StoppablePublisherActor[ProducedMessage] {

  implicit val sys = context.system

  val StatsdParser = "([^:]+):([^|]+)\\|(\\w+)".r

  val host = props ~> 'host | "localhost"
  val port = props +> 'port | 12345
  val parsePayload = props ?> 'parsePayload | true
  var openSocket: Option[ActorRef] = None

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"About to start Statsd listener $id")
  }


  override def postStop(): Unit = {
    closePort()
    super.postStop()
  }

  override def becomeActive(): Unit = {
    openPort()
    logger.info(s"Becoming active - new accepting messages from statsd [$id]")
    super.becomeActive()
  }

  override def becomePassive(): Unit = {
    closePort()
    logger.info(s"Becoming passive - no longer accepting messages from gate [$id] - all messages will be dropped")
    super.becomePassive()
  }

  def handler: Receive = {
    case Udp.Received(data, remote) => enqueue(data)
    case Udp.Bound(local) => openSocket = Some(sender())
    case Udp.Unbound =>
      logger.debug(s"Unbound!")
      context.stop(self)
      openSocket = None
  }

  private def openPort() =
    IO(Udp) ! Udp.Bind(self, new InetSocketAddress(host, port))

  private def closePort() = {
    openSocket.foreach { actor =>
      logger.debug(s"Unbind -> $actor")
      actor ! Unbind
    }
  }

  private def parseStatsdMessage(data: String): JsValue = data match {
    case StatsdParser(b, v, t) =>

      val value = t match {
        case "s" => \/-(JsString(v))
        case _ => Try(\/-(JsNumber(BigDecimal(v)))).recover {
          case _ =>
            logger.warn(s"Unparsable number in the statsd payload $data")
            -\/(s"Unparsable number in the statsd payload $data")
        }.get
      }
      value match {
        case -\/(x) => Json.obj(
          "valid" -> false,
          "error" -> x
        )
        case \/-(value) => Json.obj(
          "valid" -> true,
          "bucket" -> b,
          "value" -> value,
          "type" -> t
        )

      }
    case s =>
      logger.warn(s"Invalid statsd payload: $data")
      Json.obj(
        "valid" -> false
      )
  }

  private def parse(data: String) = {
    ProducedMessage(
    Json.obj(
      "statsd" -> (if (!parsePayload) JsString(data) else parseStatsdMessage(data))), None)
  }


  private def enqueue(data: ByteString) = {
    if (isComponentActive) {
      logger.info(s"Statsd input $id received: $data")
      val elements = data.utf8String.split('\n')
      elements foreach { v => forwardToFlow(parse(v)) }

      // TODO at the moment we are using unbounded queue and UDP are not back-pressured, so it is possible to hit OOM
      // to fix this we need a combination of aggregation/dropping in place

    } else {
      logger.info(s"Statsd input $id is not active, message dropped: $data")
    }
  }


}
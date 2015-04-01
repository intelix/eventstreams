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

package eventstreams.sources.statsd

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.Udp.Unbind
import akka.io.{IO, Udp}
import akka.util.ByteString
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Tools.configHelper
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithActivePassiveBehaviors, StoppablePublisherActor}
import eventstreams.{EventFrame, EventAndCursor, Tools}
import play.api.libs.json.JsValue

import scala.util.Try
import scalaz.Scalaz._
import scalaz._

trait StatsdPublisherEvents extends ComponentWithBaseSysevents {
  override def componentId: String = "Eventsource.Statsd"
}

object StatsdPublisher {
  def props(config: JsValue): Props = Props(new StatsdPublisher(config))
}


class StatsdPublisher(val props: JsValue)
  extends ActorWithComposableBehavior
  with ActorWithActivePassiveBehaviors
  with StatsdPublisherEvents
  with WithSyseventPublisher
  with StoppablePublisherActor[EventAndCursor] {

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

  override def onBecameActive(): Unit = {
    openPort()
    logger.info(s"Becoming active - new accepting messages from statsd [$id] at $host:$port")
    super.onBecameActive()
  }

  override def onBecamePassive(): Unit = {
    closePort()
    logger.info(s"Becoming passive - no longer accepting messages from gate [$id] - all messages will be dropped")
    super.onBecamePassive()
  }

  def handler: Receive = {
    case Udp.Received(data, remote) =>
      enqueue(data)
    case Udp.Bound(local) =>
//      logger.info(s"!>>> Bound! $local")
      openSocket = Some(sender())
    case Udp.Unbound =>
//      logger.debug(s"Unbound!")
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

  private def parseStatsdMessage(data: String) = data match {
    case StatsdParser(b, v, t) =>

      val value = t match {
        case "s" => \/-(v)
        case _ => Try(\/-(BigDecimal(v))).recover {
          case _ =>
            logger.warn(s"Unparsable number in the statsd payload $data")
            -\/(s"Unparsable number in the statsd payload $data")
        }.get
      }
      value match {
        case -\/(x) => Map(
          "valid" -> false,
          "error" -> x
        )
        case \/-(value) => Map(
          "valid" -> true,
          "bucket" -> b,
          "value" -> value,
          "type" -> t
        )

      }
    case s =>
      logger.warn(s"Invalid statsd payload: $data")
      Map(
        "valid" -> false
      )
  }

  private def parse(data: String) = {
    EventAndCursor(
      EventFrame(
      "statsd" -> (if (!parsePayload) data else parseStatsdMessage(data))), None)
  }


  private def enqueue(data: ByteString) = {
    if (isComponentActive) {
      logger.info(s"Statsd input $id received: $data")
      val elements = data.utf8String.split('\n')
      elements foreach { v => pushSingleEventToStream(parse(v)) }

      // TODO at the moment we are using unbounded queue and UDP are not back-pressured, so it is possible to hit OOM
      // to fix this we need a combination of aggregation/dropping in place

    } else {
      logger.info(s"Statsd input $id is not active, message dropped: $data")
    }
  }


}
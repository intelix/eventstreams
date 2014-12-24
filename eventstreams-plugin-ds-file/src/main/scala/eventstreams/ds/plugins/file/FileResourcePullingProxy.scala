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

package eventstreams.ds.plugins.file

import java.nio.charset.Charset

import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage
import akka.util.ByteString
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithTicks, PipelineWithStatesActor, ShutdownablePublisherActor}
import eventstreams.core.agent.core.{Cursor, ProducedMessage}
import play.api.libs.json._

import scala.annotation.tailrec
import scalaz.Scalaz._


object FileMonitorActorPublisher {

  def props(flowId: String, targetProps: JsValue, cursor: Option[JsValue] = None)
           (implicit fileIndexing: Indexer, charset: Charset) = Props(
    new PullingActorPublisher(new FileResourcePullingProxy(flowId, targetProps), cursor))

}


class FileResourcePullingProxy(flowId: String, targetProps: JsValue)(implicit indexer: Indexer) extends ResourcePullingProxy{

  var cancelled = false

  val indexerSession = indexer.startSession(flowId, RollingFileMonitorTarget(
    (targetProps \ "directory").as[String],
    (targetProps \ "mainPattern").as[String],
    (targetProps \ "rollingPattern").as[String],
    (targetProps \ "startWith").asOpt[String].map(_.toLowerCase) match {
      case Some("first") => StartWithFirst()
      case _ => StartWithLast()
    },
    (targetProps \ "fileOrdering").asOpt[String].map(_.toLowerCase) match {
      case Some("name only") => OrderByNameOnly()
      case _ => OrderByLastModifiedAndName()
    }
  ))



  private def initialCursor : Cursor = {
    indexerSession.tailCursor
  }

  override def next(c: Option[Cursor]): Option[DataChunk] = {
    if (cancelled) return None
    val cursor = c getOrElse initialCursor
    indexerSession.withOpenResource(cursor) { resource =>
      resource.nextChunk()
    }
  }

  override def cancelResource(): Unit = {
    cancelled = true
    indexerSession.close()
  }

}




class PullingActorPublisher(val proxy: ResourcePullingProxy, val initialCursor: Option[JsValue])

  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithTicks
  with ShutdownablePublisherActor[ProducedMessage] {

  private var currentCursor : Option[JsValue] = initialCursor


  override def processTick(): Unit = {
    lastRequestedState match {
      case Some(Active()) => pullAndReleaseNext()
      case _ => ()
    }
  }


  override def becomeActive(): Unit = {
    logger.info("Publisher becoming active")
    switchToCustomBehavior(commonBehavior orElse handlePublisherMessages, Some("active"))
  }

  override def becomePassive(): Unit = {
    logger.info("Publisher becoming passive")
    switchToCommonBehavior()
  }

  private def handlePublisherMessages: Receive = {
    case ActorPublisherMessage.Request(n) =>
      logger.debug(s"Downstream requested $n entries")
      pullAndReleaseNext()
    case ActorPublisherMessage.Cancel =>
      logger.debug(s"Downstream cancels the stream")
      proxy.cancelResource()
  }

  private def convertPayload(b: ByteString, meta: Option[JsValue]) = Json.obj(
    "value" -> JsString(b.utf8String),
    "meta" -> (meta | Json.obj())
  )

  @tailrec
  private def pullAndReleaseNext(): Unit = {
    if (totalDemand > 0 && isActive) {
      val entry = proxy.next(FileCursorTools.fromJson(currentCursor))

      entry match {
        case Some(e) =>

          currentCursor = FileCursorTools.toJson(e.cursor)

          if (e.data.isDefined) {
            onNext(ProducedMessage(convertPayload(e.data.get, e.meta), currentCursor))
            logger.info(s"Published next entry, current cursor: $currentCursor")

            if (e.hasMore)
              pullAndReleaseNext()
            else
              logger.debug(s"Reached tail")
          }
        case None =>
          logger.info(s"No data available")
      }
    }
  }


}





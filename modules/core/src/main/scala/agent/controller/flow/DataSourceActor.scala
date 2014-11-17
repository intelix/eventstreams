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

package agent.controller.flow

import java.nio.charset.Charset

import agent.flavors.files._
import agent.shared._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import akka.util.ByteString
import common.actors.{Acknowledged, ActorWithComposableBehavior}
import common.{BecomeActive, BecomePassive}
import play.api.libs.json._
import play.api.libs.json.extensions._


object DataSourceActor {
  def props(dsId: Long, config: JsValue, state: Option[JsValue])(implicit mat: FlowMaterializer, system: ActorSystem) = Props(new DataSourceActor(dsId, config, state))
}


case class DataSourceStateUpdate(id: Long, state: JsValue)
case class DataSourceConfigUpdate(id: Long, config: JsValue)

class DataSourceActor(dsId: Long, config: JsValue, state: Option[JsValue])(implicit mat: FlowMaterializer) extends ActorWithComposableBehavior {

  type In = ProducedMessage[ByteString, Cursor]
  type Out = ProducedMessage[MessageWithAttachments[ByteString], Cursor]
  var commProxy: Option[ActorRef] = None
  var active = false
  private var flow: Option[FlowInstance] = None
  private var cursor2config: Option[Cursor => Option[JsValue]] = None

  override def preStart(): Unit = {
    super.preStart()
    createFlowFromConfig()
    switchToCustomBehavior(suspended)
  }

  override def commonBehavior: Receive = super.commonBehavior orElse {
    case Acknowledged(id, msg) => msg match {
      case ProducedMessage(_, c: Cursor) =>
        for (
          func <- cursor2config;
          config <- func(c)
        ) context.parent ! DataSourceStateUpdate(id, config)
    }
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
  }

  private def createFlowFromConfig(): Unit = {

    import play.api.libs.json.Reads._
    import play.api.libs.json._

    logger.info(s"Creating flow $dsId, config $config, initial state $state")

    implicit val dispatcher = context.system.dispatcher
    implicit val mat = FlowMaterializer()


    def buildProcessorFlow(props: JsValue): Flow[In, Out] = {
      val convert = Flow[In].map {
        case ProducedMessage(msg, c) =>
          ProducedMessage(MessageWithAttachments(msg, Json.obj()), c)
      }

      val setSource = Flow[Out].map {
        case ProducedMessage(MessageWithAttachments(msg, json), c) =>
          val modifiedJson = json set __ \ "sourceId" -> JsString((props \ "sourceId").asOpt[String].getOrElse("undefined"))
          ProducedMessage(MessageWithAttachments(msg, modifiedJson), c)
      }

      val setTags = Flow[Out].map {
        case ProducedMessage(MessageWithAttachments(msg, json), c) =>
          val modifiedJson = json set __ \ "tags" -> (props \ "tags").asOpt[JsArray].getOrElse(Json.arr())
          ProducedMessage(MessageWithAttachments(msg, modifiedJson), c)
      }

      convert.via(setSource).via(setTags)
    }


    def buildFileMonitorTarget(targetProps: JsValue): MonitorTarget = {

      logger.debug(s"Creating RollingFileMonitorTarget from $targetProps")

      RollingFileMonitorTarget(
        (targetProps \ "directory").as[String],
        (targetProps \ "mainPattern").as[String],
        (targetProps \ "rollPattern").as[String],
        f => f.lastModified())
    }

    def buildState(): Option[Cursor] = {
      for (
        stateCfg <- state;
        fileCursorCfg <- (stateCfg \ "fileCursor").asOpt[JsValue];
        seed <- (fileCursorCfg \ "idx" \ "seed").asOpt[Long];
        resourceId <- (fileCursorCfg \ "idx" \ "rId").asOpt[Long];
        positionWithinItem <- (fileCursorCfg \ "pos").asOpt[Long]
      ) yield {
        val state = FileCursor(ResourceIndex(seed, resourceId), positionWithinItem)
        logger.info(s"Initial state: $state")
        state
      }
    }

    def buildFileProducer(fId: Long, props: JsValue): Props = {
      implicit val charset = Charset.forName("UTF-8")
      implicit val fileIndexing = new FileIndexer

      logger.debug(s"Building FileMonitorActorPublisher from $props")

      cursor2config = Some {
        case FileCursor(ResourceIndex(seed, resourceId), positionWithinItem) =>
          Some(Json.obj(
            "fileCursor" -> Json.obj(
              "idx" -> Json.obj(
                "seed" -> seed,
                "rId" -> resourceId),
              "pos" -> positionWithinItem)))
        case _ => None
      }

      FileMonitorActorPublisher.props(fId, buildFileMonitorTarget(props \ "target"), buildState())
    }

    def buildProducer(fId: Long, config: JsValue): Props = {

      logger.debug(s"Building producer from $config")

      (config \ "class").asOpt[String] match {
        case Some("file") => buildFileProducer(fId, config \ "props")
        case _ => throw new IllegalArgumentException(Json.stringify(config))
      }
    }


    def buildAkkaSink(fId: Long, props: JsValue): Props = {
      SubscriberBoundaryInitiatingActor.props((props \ "url").as[String])
    }

    def buildSink(fId: Long, config: JsValue): Props = {
      (config \ "class").asOpt[String] match {
        case Some("akka") => buildAkkaSink(fId, config \ "props")
        case _ => BlackholeAutoAckSinkActor.props
      }
    }



    val publisherProps = buildProducer(dsId, config \ "source")
    val publisherActor: ActorRef = context.actorOf(publisherProps)
    val publisher = PublisherSource(ActorPublisher[In](publisherActor))

    val processingSteps = buildProcessorFlow(config \ "preprocessing")

    val sinkProps = buildSink(dsId, config \ "endpoint")
    val sinkActor = context.actorOf(sinkProps)
    val sink = SubscriberSink(ActorSubscriber[Out](sinkActor))

    val runnableFlow: RunnableFlow = publisher.via(processingSteps).to(sink)

    val materializedFlow: MaterializedMap = runnableFlow.run()

    flow = Some(FlowInstance(materializedFlow, publisherActor, sinkActor))

  }

  private def startFlow(): Unit = {
    flow.foreach { v =>
      v.sink ! BecomeActive()
      v.source ! BecomeActive()
    }
    active = true
    sendToHQAll()
  }

  private def stopFlow(): Unit = {
    flow.foreach { v =>
      v.source ! BecomePassive()
      v.sink ! BecomePassive()
    }
    active = false
    sendToHQAll()
  }

  private def info: JsValue = Json.obj("info" -> Json.obj("id" -> "Test", "name" -> "temp name", "state" -> (if (active) "active" else "passive"))) // TODO

  private def sendToHQAll() = {
    sendToHQ(info)
    //    sendToHQ(snapshot)
  }

  private def sendToHQ(json: JsValue) = {
    commProxy foreach { actor =>
      logger.debug(s"$json -> $actor")
      actor ! GenericJSONMessage(Json.stringify(json))
    }
  }

  private def suspended: Receive = {
    case OpenTap() =>
      startFlow()
      switchToCustomBehavior(started)
  }

  private def started: Receive = {
    case CloseTap() =>
      stopFlow()
      switchToCustomBehavior(suspended)
  }

  case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)

}

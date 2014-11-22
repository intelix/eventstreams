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
import akka.actor.{ActorRefFactory, ActorRef, ActorSystem, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import akka.util.ByteString
import common.actors._
import common.{BecomeActive, BecomePassive, Stop}
import play.api.libs.json._
import play.api.libs.json.extensions._


object DatasourceActor {
  def props(dsId: String)(implicit mat: FlowMaterializer) = Props(new DatasourceActor(dsId))
  def start(dsId: String)(implicit mat: FlowMaterializer, f: ActorRefFactory) =  f.actorOf(props(dsId), ActorTools.actorFriendlyId(dsId))
}


class DatasourceActor(dsId: String)(implicit mat: FlowMaterializer)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithConfigStore {

  type In = ProducedMessage[ByteString, Cursor]
  type Out = ProducedMessage[MessageWithAttachments[ByteString], Cursor]
  var commProxy: Option[ActorRef] = None
  var active = false
  private var flow: Option[FlowInstance] = None
  private var cursor2config: Option[Cursor => Option[JsValue]] = None

  override def storageKey: Option[String] = Some(s"ds/$dsId")

  override def becomeActive(): Unit = {
    startFlow()
    super.becomeActive()
  }

  override def becomePassive(): Unit = {
    stopFlow()
    super.becomePassive()
  }

  override def commonBehavior: Receive = super.commonBehavior orElse {
    case Acknowledged(id, msg) => msg match {
      case ProducedMessage(_, c: Cursor) =>
        for (
          func <- cursor2config;
          state <- func(c)
        ) updateConfigState(Some(state)) // TODO (low priority) aggregate and send updates every sec or so, and on postStop
    }
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case ReconfigureTap(data) =>
      updateConfigProps(data)
    case ReconfigureTap() =>
      updateConfigState(None)
  }


  override def applyConfig(key: String, config: JsValue, state: Option[JsValue]): Unit = {

    import play.api.libs.json.Reads._
    import play.api.libs.json._

    logger.info(s"Creating flow $dsId, config $config, initial state $state")

    implicit val dispatcher = context.system.dispatcher
    implicit val mat = FlowMaterializer()

    flow.foreach { v =>
      v.source ! BecomePassive()
      v.source ! Stop(Some("Applying new configuration"))
      v.sink ! Stop(Some("Applying new configuration"))
    }

    flow = None

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

    def buildFileProducer(fId: String, props: JsValue): Props = {
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

    def buildProducer(fId: String, config: JsValue): Props = {

      logger.debug(s"Building producer from $config")

      (config \ "class").asOpt[String] match {
        case Some("file") => buildFileProducer(fId, config \ "props")
        case _ => throw new IllegalArgumentException(Json.stringify(config))
      }
    }


    def buildAkkaSink(fId: String, props: JsValue): Props = {
      SubscriberBoundaryInitiatingActor.props((props \ "url").as[String])
    }

    def buildSink(fId: String, config: JsValue): Props = {
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

    if (isPipelineActive) startFlow()
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
  }

  private def sendToHQ(json: JsValue) = {
    commProxy foreach { actor =>
      logger.debug(s"$json -> $actor")
      actor ! GenericJSONMessage(Json.stringify(json))
    }
  }


}

case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)


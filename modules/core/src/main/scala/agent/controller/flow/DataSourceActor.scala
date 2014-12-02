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

import agent.controller.AgentMessagesV1.{DatasourceConfig, DatasourceInfo}
import agent.controller.DatasourceAvailable
import agent.flavors.files._
import agent.shared._
import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import akka.util.ByteString
import common.actors._
import common.{BecomeActive, BecomePassive, Stop}
import hq.ComponentKey
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

  override def storageKey: Option[String] = Some(dsId)

  val key = ComponentKey(dsId)

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
          state <- func(c);
          cfg <- propsConfig
        ) updateConfigSnapshot(cfg, Some(state)) // TODO (low priority) aggregate and send updates every sec or so, and on postStop
    }
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case ReconfigureTap(data) =>
      updateConfigProps(data)
    case ResetTapState() =>
      updateConfigState(None)
    case RemoveTap() =>
      removeConfig()
      context.stop(self)
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

      logger.debug(s"Building processor flow from $props")

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
          val modifiedJson = json set __ \ "tags" -> (props \ "tags").asOpt[String].map(_.split(",").map(_.trim)).map(Json.toJson(_)).getOrElse(Json.arr())
          ProducedMessage(MessageWithAttachments(msg, modifiedJson), c)
      }

      convert.via(setSource).via(setTags)
    }


    def buildFileMonitorTarget(targetProps: JsValue): MonitorTarget = {

      logger.debug(s"Creating RollingFileMonitorTarget from $targetProps")

      RollingFileMonitorTarget(
        (targetProps \ "directory").as[String],
        (targetProps \ "mainPattern").as[String],
        (targetProps \ "rollingPattern").as[String],
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

      FileMonitorActorPublisher.props(fId, buildFileMonitorTarget(props), buildState())
    }

    def buildProducer(fId: String, config: JsValue): Props = {

      logger.debug(s"Building producer from $config")

      (config \ "class").asOpt[String] match {
        case Some("file") => buildFileProducer(fId, config)
        case _ => throw new IllegalArgumentException(Json.stringify(config))
      }
    }


    def buildAkkaSink(fId: String, props: JsValue): Props = {
      SubscriberBoundaryInitiatingActor.props((props \ "url").as[String])
    }

    def buildSink(fId: String, config: JsValue): Props = {
      (config \ "class").asOpt[String] match {
        case Some("akka") => buildAkkaSink(fId, config)
        case _ => BlackholeAutoAckSinkActor.props
      }
    }



    val publisherProps = buildProducer(dsId, config \ "source")
    val publisherActor: ActorRef = context.actorOf(publisherProps)
    val publisher = PublisherSource(ActorPublisher[In](publisherActor))

    val processingSteps = buildProcessorFlow(config)

    val sinkProps = buildSink(dsId, config \ "sink")
    val sinkActor = context.actorOf(sinkProps)
    val sink = SubscriberSink(ActorSubscriber[Out](sinkActor))

    val runnableFlow: RunnableFlow = publisher.via(processingSteps).to(sink)

    val materializedFlow: MaterializedMap = runnableFlow.run()

    flow = Some(FlowInstance(materializedFlow, publisherActor, sinkActor))

    if (isPipelineActive) startFlow()

  }


  override def onInitialConfigApplied(): Unit = context.parent ! DatasourceAvailable(key)


  override def afterApplyConfig(): Unit = {
    sendToHQ(props)
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

  private def props =
    DatasourceConfig(propsConfig.getOrElse(Json.obj()))
  private def info =
    DatasourceInfo(Json.obj(
      "id" -> "Test",
      "name" -> "temp name",
      "state" -> (if (active) "active" else "passive")
    ))

  private def sendToHQAll() = {
    sendToHQ(info)
    sendToHQ(props)
  }

  private def sendToHQ(msg: Any) = {
    commProxy foreach { actor =>
      logger.debug(s"$msg -> $actor")
      actor ! msg
    }
  }


}

case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)


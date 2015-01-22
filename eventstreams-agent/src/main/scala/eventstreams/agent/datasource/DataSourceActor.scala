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

package eventstreams.agent.datasource

import java.util.Date

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import com.typesafe.config.Config
import core.events.EventOps.symbolToEventOps
import core.events.ref.ComponentWithBaseEvents
import core.events.{FieldAndValue, WithEventPublisher}
import eventstreams.agent.DatasourceAvailable
import eventstreams.core.Tools.configHelper
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.agent.core._
import eventstreams.core.ds.AgentMessagesV1.{DatasourceConfig, DatasourceInfo}
import eventstreams.core.messages.{ComponentKey, TopicKey}
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz._

trait DatasourceActorEvents extends ComponentWithBaseEvents with BaseActorEvents with StateChangeEvents {
  override def componentId: String = "Actor.Datasource"

  val DatasourceReady = 'DatasourceReady.info

  val CreatingFlow = 'CreatingFlow.trace
  val CreatingProducer = 'CreatingProducer.trace
  val CreatingProcessors = 'CreatingProcessors.trace
  val CreatingSink = 'CreatingSink.trace

  val MessageToDatasourceProxy = 'MessageToDatasourceProxy.trace


}

object DatasourceActor extends DatasourceActorEvents {
  def props(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config) = Props(new DatasourceActor(dsId, dsConfigs))

  def start(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, f: ActorRefFactory, sysconfig: Config) = f.actorOf(props(dsId, dsConfigs), ActorTools.actorFriendlyId(dsId))
}


sealed trait DatasourceState {
  def details: Option[String]
}

case class DatasourceStateUnknown(details: Option[String] = None) extends DatasourceState

case class DatasourceStateActive(details: Option[String] = None) extends DatasourceState

case class DatasourceStatePassive(details: Option[String] = None) extends DatasourceState

case class DatasourceStateError(details: Option[String] = None) extends DatasourceState


class DatasourceActor(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithConfigStore
  with ActorWithPeriodicalBroadcasting
  with DatasourceActorEvents with WithEventPublisher {

  val allBuilders = dsConfigs.map { cfg =>
    Class.forName(cfg.getString("class")).newInstance().asInstanceOf[BuilderFromConfig[Props]]
  }

  val key = ComponentKey(dsId)
  var currentState: DatasourceState = DatasourceStateUnknown(Some("Initialising"))


  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> key.key, 'State -> stateAsString)

  private var endpointDetails = "N/A"
  private var commProxy: Option[ActorRef] = None
  private var flow: Option[FlowInstance] = None

  override def storageKey: Option[String] = Some(dsId)


  def stateAsString = currentState match {
    case DatasourceStateUnknown(_) => "unknown"
    case DatasourceStateActive(_) => "active"
    case DatasourceStatePassive(_) => "passive"
    case DatasourceStateError(_) => "error"
  }

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  override def becomeActive(): Unit = {
    startFlow()
    super.becomeActive()
  }

  override def becomePassive(): Unit = {
    stopFlow()
    super.becomePassive()
  }

  override def commonBehavior: Receive = super.commonBehavior orElse {
    case Acknowledged(_, Some(msg)) => msg match {
      case c: JsValue => propsConfig.foreach { propsConfig => updateWithoutApplyConfigSnapshot(propsConfig, Some(c))}
      case _ => ()
    }
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHQAll()
    case ReconfigureTap(data) =>
      updateAndApplyConfigProps(data)
    case ResetTapState() =>
      updateAndApplyConfigState(None)
    case RemoveTap() =>
      removeConfig()
      context.stop(self)
  }

  override def applyConfig(key: String, config: JsValue, state: Option[JsValue]): Unit = {

    implicit val dispatcher = context.system.dispatcher

    implicit val mat = FlowMaterializer()

    flow.foreach { v =>
      v.source ! BecomePassive()
      v.source ! Stop(Some("Applying new configuration"))
      v.sink ! Stop(Some("Applying new configuration"))
    }

    flow = None

    def buildProcessorFlow(props: JsValue): Flow[ProducedMessage, ProducedMessage] = {

      CreatingFlow >>()

      val setSource = Flow[ProducedMessage].map {
        case ProducedMessage(frame, c) =>
          ProducedMessage(frame + ("sourceId" -> (props ~> "sourceId" | "undefined")), c)
      }

      val setTags = Flow[ProducedMessage].map {
        case ProducedMessage(json, c) =>
          val v: Seq[String] = (props ~> "tags").map(_.split(",").map(_.trim).toSeq).getOrElse(Seq[String]())
          ProducedMessage(json + ("tags" -> v), c)
      }

      setSource.via(setTags)
    }

    def buildProducer(fId: String, config: JsValue): \/[Fail, Props] = {

      CreatingProducer >> ('Props -> config)

      for (
        instClass <- config ~> 'class \/> Fail("Invalid datasource config: missing 'class' value");
        builder <- allBuilders.find(_.configId == instClass)
          \/> Fail(s"Unsupported or invalid datasource class $instClass. Supported classes: ${allBuilders.map(_.configId)}");
        impl <- builder.build(config, state, Some(fId))
      ) yield impl

    }


    def buildSink(fId: String, props: JsValue): \/[Fail, Props] = {
      CreatingSink >> ('Props -> config)

      for (
        endpoint <- props ~> 'targetGate \/> Fail("Invalid datasource config: missing 'targetGate' value");
        impl <- SubscriberBoundaryInitiatingActor.props(endpoint, props +> 'maxInFlight | 1000).right
      ) yield {
        endpointDetails = endpoint
        impl
      }
    }



    val result = for (
      publisherProps <- buildProducer(dsId, config \ "source");
      sinkProps <- buildSink(dsId, config)
    ) yield {
      val publisherActor: ActorRef = context.actorOf(publisherProps)
      val publisher = PublisherSource(ActorPublisher[ProducedMessage](publisherActor))

      val processingSteps = buildProcessorFlow(config)

      val sinkActor = context.actorOf(sinkProps)
      val sink = SubscriberSink(ActorSubscriber[ProducedMessage](sinkActor))

      val runnableFlow: RunnableFlow = publisher.via(processingSteps).to(sink)

      val materializedFlow: MaterializedMap = runnableFlow.run()

      flow = Some(FlowInstance(materializedFlow, publisherActor, sinkActor))

      if (isComponentActive)
        startFlow()
      else
        stopFlow()
    }

    result match {
      case -\/(fail) =>
        Warning >>('Message -> "Unable to build datasource", 'Reason -> fail)
        currentState = DatasourceStateError(fail.message)
      case _ =>
        DatasourceReady >>()
    }


  }

  override def onInitialConfigApplied(): Unit = context.parent ! DatasourceAvailable(key)

  override def afterApplyConfig(): Unit = {
    sendToHQAll()
  }

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (TopicKey("info"), 5, () => info, sendToHQ _)
  )

  private def name = propsConfig ~> 'name | "N/A"

  private def created = prettyTime.format(new Date(propsConfig ++> 'created | now))

  private def startFlow(): Unit = {
    flow.foreach { v =>
      v.sink ! BecomeActive()
      v.source ! BecomeActive()
    }
    currentState = DatasourceStateActive()
    sendToHQAll()
  }

  private def stopFlow(): Unit = {
    flow.foreach { v =>
      v.source ! BecomePassive()
      v.sink ! BecomePassive()
    }
    currentState = DatasourceStatePassive()
    sendToHQAll()
  }

  private def props =
    DatasourceConfig(propsConfig.getOrElse(Json.obj()))

  private def info =
    DatasourceInfo(Json.obj(
      "id" -> "Test",
      "created" -> created,
      "name" -> name,
      "endpointType" -> "Gate",
      "endpointDetails" -> endpointDetails,
      "sinceStateChange" -> prettyTimeSinceStateChange,
      "state" -> stateAsString,
      "stateDetails" -> stateDetailsAsString
    ))

  private def sendToHQAll() = {
    sendToHQ(info)
    sendToHQ(props)
  }

  private def sendToHQ(msg: Any) = {
    commProxy foreach { actor =>
      MessageToDatasourceProxy >> ('Message -> msg)
      actor ! msg
    }
  }
}

case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)


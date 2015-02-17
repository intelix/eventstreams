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

package eventstreams.agent

import java.util.Date

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import _root_.core.sysevents.{FieldAndValue, WithSyseventPublisher}
import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import com.typesafe.config.Config
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.agent.AgentMessagesV1.{EventsourceConfig, EventsourceInfo}
import eventstreams.core.actors._
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz._

trait EventsourceActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with StateChangeSysevents {
  override def componentId: String = "Agent.Eventsource"

  val EventsourceReady = 'EventsourceReady.info

  val CreatingFlow = 'CreatingFlow.trace
  val CreatingProducer = 'CreatingProducer.trace
  val CreatingProcessors = 'CreatingProcessors.trace
  val CreatingSink = 'CreatingSink.trace

  val MessageToEventsourceProxy = 'MessageToEventsourceProxy.trace


}

object EventsourceActor extends EventsourceActorSysevents {
  def props(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config) = Props(new EventsourceActor(dsId, dsConfigs))

  def start(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, f: ActorRefFactory, sysconfig: Config) = f.actorOf(props(dsId, dsConfigs), ActorTools.actorFriendlyId(dsId))
}


sealed trait EventsourceState {
  def details: Option[String]
}

case class EventsourceStateUnknown(details: Option[String] = None) extends EventsourceState

case class EventsourceStateActive(details: Option[String] = None) extends EventsourceState

case class EventsourceStatePassive(details: Option[String] = None) extends EventsourceState

case class EventsourceStateError(details: Option[String] = None) extends EventsourceState


class EventsourceActor(dsId: String, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithConfigStore
  with ActorWithPeriodicalBroadcasting
  with EventsourceActorSysevents with WithSyseventPublisher {

  val allBuilders = dsConfigs.map { cfg =>
    Class.forName(cfg.getString("class")).newInstance().asInstanceOf[BuilderFromConfig[Props]]
  }

  val key = ComponentKey(dsId)
  var currentState: EventsourceState = EventsourceStateUnknown(Some("Initialising"))


  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> key.key, 'State -> stateAsString)

  private var endpointDetails = "N/A"
  private var commProxy: Option[ActorRef] = None
  private var flow: Option[FlowInstance] = None

  override def storageKey: Option[String] = Some(dsId)


  def stateAsString = currentState match {
    case EventsourceStateUnknown(_) => "unknown"
    case EventsourceStateActive(_) => "active"
    case EventsourceStatePassive(_) => "passive"
    case EventsourceStateError(_) => "error"
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
    case ReconfigureEventsource(data) =>
      updateAndApplyConfigProps(Json.parse(data))
    case ResetEventsourceState() =>
      updateAndApplyConfigState(None)
    case RemoveEventsource() =>
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
        instClass <- config ~> 'class \/> Fail("Invalid eventsource config: missing 'class' value");
        builder <- allBuilders.find(_.configId == instClass)
          \/> Fail(s"Unsupported or invalid eventsource class $instClass. Supported classes: ${allBuilders.map(_.configId)}");
        impl <- builder.build(config, state, Some(fId))
      ) yield impl

    }


    def buildSink(fId: String, props: JsValue): \/[Fail, Props] = {
      CreatingSink >> ('Props -> config)

      for (
        endpoint <- props ~> 'targetGate \/> Fail("Invalid eventsource config: missing 'targetGate' value");
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
        Warning >>('Message -> "Unable to build eventsource", 'Reason -> fail)
        currentState = EventsourceStateError(fail.message)
      case _ =>
        EventsourceReady >>()
    }


  }

  override def onInitialConfigApplied(): Unit = context.parent ! EventsourceAvailable(key)

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
    currentState = EventsourceStateActive()
    sendToHQAll()
  }

  private def stopFlow(): Unit = {
    flow.foreach { v =>
      v.source ! BecomePassive()
      v.sink ! BecomePassive()
    }
    currentState = EventsourceStatePassive()
    sendToHQAll()
  }

  private def props =
    EventsourceConfig(propsConfig.getOrElse(Json.obj()))

  private def info =
    EventsourceInfo(Json.obj(
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
      MessageToEventsourceProxy >> ('Message -> msg)
      actor ! msg
    }
  }
}

case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)


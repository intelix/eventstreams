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
import eventstreams.Tools.{configHelper, optionsHelper}
import eventstreams._
import eventstreams.agent.AgentMessagesV1.{EventsourceConfig, EventsourceInfo}
import eventstreams.core.actors._
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz._

trait EventsourceActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with StateChangeSysevents {
  val EventsourceReady = 'EventsourceReady.info
  val CreatingFlow = 'CreatingFlow.trace
  val CreatingProducer = 'CreatingProducer.trace
  val CreatingProcessors = 'CreatingProcessors.trace
  val CreatingSink = 'CreatingSink.trace
  val MessageToEventsourceProxy = 'MessageToEventsourceProxy.trace

  override def componentId: String = "Agent.Eventsource"


}

object EventsourceActor extends EventsourceActorSysevents {
  def props(dsId: String, config: ModelConfigSnapshot, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config) =
    Props(new EventsourceActor(dsId, config, dsConfigs))

  def start(dsId: String, config: ModelConfigSnapshot, dsConfigs: List[Config])(implicit mat: FlowMaterializer, f: ActorRefFactory, sysconfig: Config) =
    f.actorOf(props(dsId, config, dsConfigs), ActorTools.actorFriendlyId(dsId))
}


sealed trait EventsourceState {
  def details: Option[String]
}

case class EventsourceStateUnknown(details: Option[String] = None) extends EventsourceState

case class EventsourceStateActive(details: Option[String] = None) extends EventsourceState

case class EventsourceStatePassive(details: Option[String] = None) extends EventsourceState

case class EventsourceStateError(details: Option[String] = None) extends EventsourceState


class EventsourceActor(val entityId: String, val initialConfig: ModelConfigSnapshot, dsConfigs: List[Config])(implicit mat: FlowMaterializer, sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithActivePassiveBehaviors
  with GenericModelInstance
  with ActorWithPeriodicalBroadcasting
  with EventsourceActorSysevents
  with WithSyseventPublisher {

  val allBuilders = dsConfigs.map { cfg =>
    Class.forName(cfg.getString("class")).newInstance().asInstanceOf[BuilderFromConfig[Props]]
  }

  var currentState: EventsourceState = EventsourceStateUnknown(Some("Initialising"))
  private var endpointDetails = "N/A"
  private var commProxy: Option[ActorRef] = None
  private var flow: Option[FlowInstance] = None

  override def commonFields: Seq[FieldAndValue] = super.commonFields ++ Seq('ComponentKey -> entityId, 'State -> stateAsString)

  override def preStart(): Unit = {
    super.preStart()
    createFlow()
  }

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

  override def onBecameActive(): Unit = {
    super.onBecameActive()
    flow match {
      case None => createFlow()
      case _ => startFlow()
    }
  }

  override def onBecamePassive(): Unit = {
    stopFlow(None)
    super.onBecamePassive()
  }

  override def commonBehavior: Receive = super.commonBehavior orElse {
    case Acknowledged(_, Some(msg)) => msg match {
      case c: JsValue => updateConfigSnapshot(propsConfig, Some(c))
      case _ => ()
    }
    case StreamClosed() =>
      becomePassive()
      destroyFlow(None)
      updateConfigState(None)
      sendToHubAll()
    case StreamClosedWithError(cause) =>
      becomePassive()
      destroyFlow(cause)
      updateConfigState(None)
      currentState = EventsourceStateError(cause)
      sendToHubAll()
    case CommunicationProxyRef(ref) =>
      commProxy = Some(ref)
      sendToHubAll()
    case ReconfigureEventsource(data) =>
      updateConfigProps(Json.parse(data))
      restartModel()
    case ResetEventsourceState() =>
      updateConfigState(None)
      restartModel()
    case RemoveEventsource() =>
      destroyModel()
  }


  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (TopicKey("info"), 5, () => info, sendToHub _)
  )

  private def destroyFlow(reason: Option[String]) = {
    flow.foreach { v =>
      v.source ! BecomePassive()
      v.source ! Stop(reason)
      v.sink ! Stop(reason)
    }

    flow = None

  }

  private def createFlow(): Unit = {
    implicit val dispatcher = context.system.dispatcher

    implicit val mat = FlowMaterializer()

    def buildProcessorFlow(streamSeed: String, props: JsValue): Flow[EventAndCursor, EventAndCursor] = {

      CreatingFlow >> ('StreamSeed -> streamSeed)

      val setSource = Flow[EventAndCursor].map {
        case EventAndCursor(frame, c) =>
          EventAndCursor(frame.setStreamKey(props ~> "streamKey" | "default").setStreamSeed(streamSeed), c)
      }

      val setTags = Flow[EventAndCursor].map {
        case EventAndCursor(frame, c) =>
          val v: Seq[String] = (props ~> "tags").map(_.split(",").map(_.trim).toSeq).getOrElse(Seq[String]())
          EventAndCursor(frame + ("tags" -> v), c)
      }

      setSource.via(setTags)
    }

    def buildProducer(streamSeed: String, config: JsValue): \/[Fail, Props] = {

      CreatingProducer >>('Props -> config, 'StreamSeed -> streamSeed)

      for (
        instClass <- config #> 'source ~> 'class orFail "Invalid eventsource config: missing 'class' value";
        builder <- allBuilders.find(_.configId == instClass)
          orFail s"Unsupported or invalid eventsource class $instClass. Supported classes: ${allBuilders.map(_.configId)}";
        impl <- builder.build(config, stateConfig, Some(streamSeed))
      ) yield impl

    }


    def buildSink(streamSeed: String, props: JsValue): \/[Fail, Props] = {
      CreatingSink >>('Props -> propsConfig, 'StreamSeed -> streamSeed)

      for (
        endpoint <- props ~> 'targetGate orFail "Invalid eventsource config: missing 'targetGate' value";
        impl <- SubscriberBoundaryInitiatingActor.props(endpoint, props +> 'maxInFlight | 1000, props +> 'maxBatchSize | 100).right
      ) yield {
        endpointDetails = endpoint
        impl
      }
    }

    destroyFlow(Some("Applying new config"))

    val streamSeed = UUIDTools.generateShortUUID

    val result = for (
      publisherProps <- buildProducer(streamSeed, propsConfig);
      sinkProps <- buildSink(streamSeed, propsConfig)
    ) yield {
        val publisherActor: ActorRef = context.actorOf(publisherProps)
        val publisher = PublisherSource(ActorPublisher[EventAndCursor](publisherActor))

        val processingSteps = buildProcessorFlow(streamSeed, propsConfig)

        val sinkActor = context.actorOf(sinkProps)
        val sink = SubscriberSink(ActorSubscriber[EventAndCursor](sinkActor))

        val runnableFlow: RunnableFlow = publisher.via(processingSteps).to(sink)

        val materializedFlow: MaterializedMap = runnableFlow.run()

        flow = Some(FlowInstance(materializedFlow, publisherActor, sinkActor))

      }

    result match {
      case -\/(fail) =>
        Warning >>('Message -> "Unable to build eventsource", 'Reason -> fail)
        currentState = EventsourceStateError(fail.message)
      case _ =>
        EventsourceReady >>()
        if (metaConfig ?> 'lastStateActive | false) {
          startFlow()
        } else {
          stopFlow(None)
        }
    }


    sendToHubAll()

  }

  private def name = propsConfig ~> 'name | "N/A"

  private def created = prettyTime.format(new Date(metaConfig ++> 'created | now))

  private def startFlow(): Unit = {
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = true))

    flow.foreach { v =>
      v.sink ! BecomeActive()
      v.source ! BecomeActive()
    }
    currentState = EventsourceStateActive()
    sendToHubAll()
  }

  private def stopFlow(errorCause: Option[String]): Unit = {
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = false))

    flow.foreach { v =>
      v.source ! BecomePassive()
      v.sink ! BecomePassive()
    }
    currentState = errorCause match {
      case Some(c) => EventsourceStateError(errorCause)
      case None => EventsourceStatePassive()
    }
    sendToHubAll()
  }

  private def props = EventsourceConfig(propsConfig)

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

  private def sendToHubAll() = {
    sendToHub(info)
    sendToHub(props)
  }

  private def sendToHub(msg: Any) = {
    commProxy foreach { actor =>
      MessageToEventsourceProxy >> ('Message -> msg)
      actor ! msg
    }
  }

  override def modelEntryInfo: Model = EventsourceAvailable(entityId, self, name)
}

case class FlowInstance(flow: MaterializedMap, source: ActorRef, sink: ActorRef)


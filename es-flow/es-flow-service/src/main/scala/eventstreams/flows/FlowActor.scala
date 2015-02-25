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

package eventstreams.flows

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import _root_.core.sysevents.{FieldAndValue, WithSyseventPublisher}
import akka.actor._
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import com.typesafe.config.Config
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import eventstreams.flows.internal.{FlowComponents, Builder}
import nl.grons.metrics.scala.MetricName
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.{-\/, \/-}



trait FlowActorSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val FlowStarted = 'FlowStarted.trace
  val FlowConfigured = 'FlowConfigured.trace

  override def componentId: String = "Flow.Flow"
}
object FlowActorSysevents extends FlowActorSysevents

object FlowActor extends FlowActorSysevents {
  def props(id: String, instructions: List[Config]) = Props(new FlowActor(id, instructions))

  def start(id: String, instructions: List[Config])(implicit f: ActorRefFactory) = f.actorOf(props(id, instructions), ActorTools.actorFriendlyId(id))
}


sealed trait FlowState {
  def details: Option[String]
}

case class FlowStateUnknown(details: Option[String] = None) extends FlowState

case class FlowStateActive(details: Option[String] = None) extends FlowState

case class FlowStatePassive(details: Option[String] = None) extends FlowState

case class FlowStateError(details: Option[String] = None) extends FlowState


class FlowActor(id: String, instructions: List[Config])
  extends PipelineWithStatesActor
  with FlowActorSysevents
  with ActorWithConfigStore
  with RouteeWithStartStopHandler
  with RouteeModelInstance
  with ActorWithPeriodicalBroadcasting
  with WithMetrics {

  implicit val mat = FlowMaterializer()
  implicit val dispatcher = context.system.dispatcher


  private var tapActor: Option[ActorRef] = None
  private var sinkActor: Option[ActorRef] = None
  private var flowActors: Option[Seq[ActorRef]] = None
  private var flow: Option[MaterializedMap] = None


  override lazy val metricBaseName: MetricName = MetricName("flow")

  val _inrate = metrics.meter(s"$id.source")
  val _outrate = metrics.meter(s"$id.sink")


  var name = "default"
  var initialState = "Closed"
  var created = prettyTimeFormat(now)
  var currentState: FlowState = FlowStateUnknown(Some("Initialising"))


  override def commonFields: Seq[FieldAndValue] = Seq('ID ->  id, 'Name -> name, 'Handler -> self)

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def publishAvailable(): Unit = context.parent ! FlowAvailable(key, self, name)

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case FlowStateUnknown(_) => "unknown"
    case FlowStateActive(_) => "active"
    case FlowStatePassive(_) => "passive"
    case FlowStateError(_) => "error"
  }


  override def info = Some(Json.obj(
    "name" -> name,
    "initial" -> initialState,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString
  ))

  def infoDynamic = currentState match {
    case FlowStateActive(_) => Some(Json.obj(
      "inrate" -> ("%.2f" format _inrate.oneMinuteRate),
      "outrate" -> ("%.2f" format _outrate.oneMinuteRate)
    ))
    case _ => Some(Json.obj())
  }


  override def onBecameActive(): Unit = {
    openFlow()
    publishInfo()
  }

  override def onBecamePassive(): Unit = {
    closeFlow()
    publishInfo()
  }

  override def autoBroadcast: List[(Key, Int, PayloadGenerator, PayloadBroadcaster)] = List(
    (T_STATS, 5, () => infoDynamic, T_STATS !! _)
  )

  def closeFlow() = {
    currentState = FlowStatePassive()
    tapActor.foreach(_ ! BecomePassive())
    flowActors.foreach(_.foreach(_ ! BecomePassive()))
  }

  override def applyConfig(key: String, config: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    initialState = config ~> 'initialState | "Closed"
    created = prettyTimeFormat(config ++> 'created | now)

    FlowConfigured >> ()

    terminateFlow(Some("Applying new configuration"))

    Builder(instructions, config, context, id) match {
      case -\/(fail) =>
        Warning >> ('Message -> "Unable to build the flow", 'Failure -> fail)
        currentState = FlowStateError(fail.message)
      case \/-(FlowComponents(tap, pipeline, sink)) =>
        resetFlowWith(tap, pipeline, sink)
    }
  }


  private def terminateFlow(reason: Option[String]) = {
    closeFlow()
    tapActor.foreach(_ ! Stop(reason))
    tapActor = None
    sinkActor = None
    flowActors = None
    flow = None
  }

  private def openFlow() = {
    currentState = FlowStateActive(Some("ok"))

    flowActors.foreach(_.foreach(_ ! BecomeActive()))
    sinkActor.foreach(_ ! BecomeActive())
    tapActor.foreach(_ ! BecomeActive())

    FlowStarted >>()
    
  }

  private def propsToActors(list: Seq[Props]) = list map context.actorOf

  private def resetFlowWith(tapProps: Props, pipeline: Seq[Props], sinkProps: Props) = {

    val tapA = context.actorOf(tapProps)
    val sinkA = context.actorOf(sinkProps)

    val pubSrc = PublisherSource[EventFrame](ActorPublisher[EventFrame](tapA))
    val subSink = SubscriberSink(ActorSubscriber[EventFrame](sinkA))

    val pipelineActors = propsToActors(pipeline)




    val flowPipeline = pipelineActors.foldRight[Sink[EventFrame]](subSink) { (actor, sink) =>
      val s = SubscriberSink(ActorSubscriber[EventFrame](actor))
      val p = PublisherSource[EventFrame](ActorPublisher[EventFrame](actor))
      p.to(sink).run()
      s
    }

    flow = Some(pubSrc.to(flowPipeline).run())

    tapActor = Some(tapA)
    sinkActor = Some(sinkA)
    flowActors = Some(pipelineActors)

    if (isComponentActive) openFlow()
  }


}


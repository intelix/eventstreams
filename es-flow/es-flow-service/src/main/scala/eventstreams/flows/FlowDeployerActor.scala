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
import akka.remote.RemoteScope
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl._
import com.typesafe.config.Config
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import eventstreams.flows.internal.{FlowComponents, Builder}
import nl.grons.metrics.scala.MetricName
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz.{-\/, \/-}



trait FlowDeployerActorSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val FlowStarted = 'FlowStarted.trace
  val FlowConfigured = 'FlowConfigured.trace

  override def componentId: String = "Flow.Flow"
}
object FlowDeployerActorSysevents extends FlowDeployerActorSysevents

object FlowDeployerActor extends FlowDeployerActorSysevents {
  def props(id: String, instructions: List[Config]) = Props(new FlowDeployerActor(id, instructions))

  def start(id: String, instructions: List[Config])(implicit f: ActorRefFactory) = f.actorOf(props(id, instructions), ActorTools.actorFriendlyId(id))
}


sealed trait FlowDeployerState {
  def details: Option[String]
}

case class FlowDeployerStateUnknown(details: Option[String] = None) extends FlowDeployerState

case class FlowDeployerStateActive(details: Option[String] = None) extends FlowDeployerState

case class FlowDeployerStatePassive(details: Option[String] = None) extends FlowDeployerState

case class FlowDeployerStateError(details: Option[String] = None) extends FlowDeployerState


class FlowDeployerActor(id: String, instructions: List[Config])
  extends PipelineWithStatesActor
  with FlowActorSysevents
  with ActorWithConfigStore
  with RouteeWithStartStopHandler
  with RouteeModelInstance
  with ActorWithPeriodicalBroadcasting
  with WithMetrics {

  var name = "default"
  var created = prettyTimeFormat(now)
  var currentState: FlowDeployerState = FlowDeployerStateUnknown(Some("Initialising"))


  override def commonFields: Seq[FieldAndValue] = Seq('ID ->  id, 'Name -> name, 'Handler -> self)

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)

  override def publishAvailable(): Unit = context.parent ! FlowAvailable(key, self, name)

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case FlowDeployerStateUnknown(_) => "unknown"
    case FlowDeployerStateActive(_) => "active"
    case FlowDeployerStatePassive(_) => "passive"
    case FlowDeployerStateError(_) => "error"
  }


  override def info = Some(Json.obj(
    "name" -> name,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString
  ))

  def infoDynamic = currentState match {
    case FlowDeployerStateActive(_) => Some(Json.obj(
//      "inrate" -> ("%.2f" format _inrate.oneMinuteRate),
//      "outrate" -> ("%.2f" format _outrate.oneMinuteRate)
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
    currentState = FlowDeployerStatePassive()
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = false))

  }




  override def applyConfig(key: String, config: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    created = prettyTimeFormat(meta ++> 'created | now)

    FlowConfigured >> ()

  }


  private def terminateFlow(reason: Option[String]) = {
    closeFlow()
  }

  private def openFlow() = {
    currentState = FlowDeployerStateActive(Some("ok"))

    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = true))

    FlowStarted >>()

  }






}



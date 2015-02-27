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

import akka.actor._
import com.typesafe.config.{ConfigFactory, Config}
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.ref.ComponentWithBaseSysevents
import core.sysevents.{FieldAndValue, WithSyseventPublisher}
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._

import scalaz.Scalaz._


trait FlowProxyActorSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val FlowStarted = 'FlowStarted.trace
  val FlowStopped = 'FlowStopped.trace
  val FlowConfigured = 'FlowConfigured.trace

  override def componentId: String = "Flow.FlowProxy"
}

object FlowProxyActorSysevents extends FlowProxyActorSysevents

object FlowProxyActor extends FlowProxyActorSysevents {
  def props(id: String, instructions: List[Config]) = Props(new FlowProxyActor(id, instructions))

  def start(id: String, instructions: List[Config])(implicit f: ActorRefFactory) = f.actorOf(props(id, instructions), ActorTools.actorFriendlyId(id))
}


sealed trait FlowProxyState {
  def details: Option[String]
}

case class FlowProxyStateUnknown(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStateActive(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStatePassive(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStateError(details: Option[String] = None) extends FlowProxyState


class FlowProxyActor(id: String, instructions: List[Config])
  extends ActorWithActivePassiveBehaviors
  with FlowProxyActorSysevents
  with ActorWithConfigStore
  with RouteeWithStartStopHandler
  with RouteeModelInstance
  with ActorWithPeriodicalBroadcasting
  with WithMetrics {

  var name = "default"
  var created = prettyTimeFormat(now)
  var currentState: FlowProxyState = FlowProxyStateUnknown(Some("Initialising"))

  var deployer: Option[ActorRef] = None

  override def commonFields: Seq[FieldAndValue] = Seq('ID -> id, 'Name -> name, 'Handler -> self)

  override def storageKey: Option[String] = Some(id)

  override def key = ComponentKey(id)


  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    deployer.foreach(context.stop)
    deployer = None
    super.postStop()
  }

  override def publishAvailable(): Unit = context.parent ! FlowAvailable(key, self, name)

  def stateDetailsAsString = currentState.details match {
    case Some(v) => stateAsString + " - " + v
    case _ => stateAsString
  }

  def stateAsString = currentState match {
    case FlowProxyStateUnknown(_) => "unknown"
    case FlowProxyStateActive(_) => "active"
    case FlowProxyStatePassive(_) => "passive"
    case FlowProxyStateError(_) => "error"
  }

  override def info = Some(Json.obj(
    "name" -> name,
    "sinceStateChange" -> prettyTimeSinceStateChange,
    "created" -> created,
    "state" -> stateAsString,
    "stateDetails" -> stateDetailsAsString
  ))

  def infoDynamic = currentState match {
    case FlowProxyStateActive(_) => Some(Json.obj(
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
    deployer.foreach(_ ! BecomePassive())
    currentState = FlowProxyStatePassive()
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = false))
    FlowStopped >>()
  }

  override def applyConfig(key: String, config: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit = {

    name = config ~> 'name | "default"
    created = prettyTimeFormat(meta ++> 'created | now)

    deployer.foreach(context.stop)
    deployer = Some(FlowDeployerActor.start(key, config, instructions))

    if (isComponentActive) deployer.foreach(_ ! BecomeActive)

    FlowConfigured >>()

  }

  private def terminateFlow(reason: Option[String]) = {
    closeFlow()
  }

  private def openFlow() = {
    deployer.foreach(_ ! BecomeActive())

    currentState = FlowProxyStateActive(Some("ok"))

    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = true))

    FlowStarted >>()

  }
}



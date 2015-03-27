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
import eventstreams.Tools.configHelper
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
  def props(id: String, config: ModelConfigSnapshot, instructions: List[Config]) = Props(new FlowProxyActor(id, config, instructions))

  def start(id: String, config: ModelConfigSnapshot, instructions: List[Config])(implicit f: ActorRefFactory) =
    f.actorOf(props(id, config, instructions), ActorTools.actorFriendlyId(id))
}


sealed trait FlowProxyState {
  def details: Option[String]
}

case class FlowProxyStateUnknown(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStateActive(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStatePassive(details: Option[String] = None) extends FlowProxyState

case class FlowProxyStateError(details: Option[String] = None) extends FlowProxyState


class FlowProxyActor(val entityId: String, val initialConfig: ModelConfigSnapshot, instructions: List[Config])
  extends ActorWithActivePassiveBehaviors
  with FlowProxyActorSysevents
  with RouteeWithStartStopHandler
  with RouteeModelInstance
  with ActorWithPeriodicalBroadcasting
  with WithCHMetrics {

  var currentState: FlowProxyState = FlowProxyStateUnknown(Some("Initialising"))
  val name = propsConfig ~> 'name | "default"
  val created = prettyTimeFormat(metaConfig ++> 'created | now)

  var deployer: ActorRef = FlowDeployerActor.start(entityId, propsConfig, instructions)


  override def preStart(): Unit = {
    deployer ! BecomeActive()

    if (metaConfig ?> 'lastStateActive | false) {
      becomeActive()
    } else {
      becomePassive()
    }

    FlowConfigured >>()
    super.preStart()
  }

  override def commonFields: Seq[FieldAndValue] = Seq('ID -> entityId, 'Name -> name, 'Handler -> self)

  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    context.stop(deployer)
    super.postStop()
  }

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
    deployer ! BecomePassive()
    currentState = FlowProxyStatePassive()
    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = false))
    FlowStopped >>()
  }

  private def terminateFlow(reason: Option[String]) = {
    closeFlow()
  }

  private def openFlow() = {
    deployer ! BecomeActive()

    currentState = FlowProxyStateActive(Some("ok"))

    updateConfigMeta(__ \ 'lastStateActive -> JsBoolean(value = true))

    FlowStarted >>()

  }

  override def modelEntryInfo: Model = FlowAvailable(entityId, self, name)
}



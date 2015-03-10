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
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.ref.ComponentWithBaseSysevents
import core.sysevents.{FieldAndValue, WithSyseventPublisher}
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._

import scalaz.Scalaz._


trait FlowDeployerActorSysevents
  extends FlowProvisionSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val UnableToRoute = 'UnableToRoute.trace

  override def componentId: String = "Flow.Deployer"
}

object FlowDeployerActorSysevents extends FlowDeployerActorSysevents

object FlowDeployerActor extends FlowDeployerActorSysevents {
  def props(id: String, config: JsValue, instructions: List[Config]) =
    Props(new FlowDeployerActor(id, config, instructions))

  def start(id: String, config: JsValue, instructions: List[Config])(implicit f: ActorRefFactory) =
    f.actorOf(props(id, config, instructions), ActorTools.actorFriendlyId(id))
}


class FlowDeployerActor(id: String, config: JsValue, instructions: List[Config])
  extends ActorWithActivePassiveBehaviors
  with ProvisionLogic
  with SinkLogic
  with FlowDeployerActorSysevents
  with WithSyseventPublisher
  with WithMetrics {

  override implicit val cluster: Cluster = Cluster(context.system)
  var provisionOnNodesWithRoles: Set[String] = (config ~> 'deployTo).map(_.split(",").map(_.trim).filter(!_.isEmpty).toSet) | Set()
  var instancesPerNode: Int = config +> 'instancesPerNode | 1
  var connectionEndpoint: Option[String] = config ~> 'sourceGateName

  override val blockGateWhenPassive: Boolean = config ?> 'blockGateWhenPassive | true

  override def commonFields: Seq[FieldAndValue] = Seq('ID -> id, 'Handler -> self)

  override def onNextEvent(e: Acknowledgeable[_]): Unit =
    destinationFor(e).foreach(_ ! e)

  override def initialiseDeployment(address: String, index: Int, ref: ActorRef): Unit =
    ref ! InitialiseDeployable(index + "@" + address, id, Json.stringify(config), instructions)

  private def destinationFor(e: Acknowledgeable[_]): Option[ActorRef] = e.msg match {
    case x: EventFrame => destinationFor(x)
    case Batch((x: EventFrame) :: _) => destinationFor(x)
    case x =>
      UnableToRoute >>('MessageId -> e.id, 'Type -> x.getClass)
      None
  }

  private def destinationFor(e: EventFrame): Option[ActorRef] =
    for (
      key <- e.streamKey;
      seed <- e.streamSeed;
      destination <- destinationFor(key, seed)
    ) yield destination

}



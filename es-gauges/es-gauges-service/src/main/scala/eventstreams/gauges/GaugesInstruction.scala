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

package eventstreams.gauges

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents._
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams._
import eventstreams.core.actors._
import eventstreams.instructions.InstructionConstants
import eventstreams.instructions.Types._
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz.\/

trait GaugesInstructionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Instruction.Gauges"

  val Built = 'Built.trace
  val StorageRequested = 'StorageScheduled.trace

}

trait GaugesInstructionConstants extends InstructionConstants with GaugesInstructionSysevents {
}

class GaugesInstruction extends BuilderFromConfig[InstructionType] with GaugesInstructionConstants {
  val configId = "gauges"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] = 
    GaugesInstructionActor.props(props).right

}

private object GaugesInstructionActor {
  def props(config: JsValue) = Props(new GaugesInstructionActor(config))
}

private class GaugesInstructionActor(config: JsValue)
  extends StoppableSubscribingPublisherActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with GaugesInstructionConstants
  with WithSyseventPublisher
  with ActorWithResolver {

  val maxInFlight = 1000

  val endpointId = ActorWithRoleId(GaugesServiceConstants.id, "gauges")
  var endpoint: Set[ActorRef] = Set.empty

  override def preStart(): Unit = {
    super.preStart()
    self ! Resolve(endpointId)
  }


  override def onActorResolved(actorId: ActorWithRoleId, ref: ActorRef): Unit = endpoint = Set(ref)

  override def onActorTerminated(actorId: ActorWithRoleId, ref: ActorRef): Unit = endpoint = Set.empty

  override def canDeliverDownstreamRightNow = isActive && isComponentActive && endpoint.nonEmpty

  override def getSetOfActiveEndpoints: Set[ActorRef] = endpoint

  override def execute(value: EventFrame): Option[Seq[EventFrame]] = {
    deliverMessage(value)
    Some(List(value))
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount + pendingToDownstreamCount
  }

  override implicit val cluster: Cluster = Cluster(context.system)

}

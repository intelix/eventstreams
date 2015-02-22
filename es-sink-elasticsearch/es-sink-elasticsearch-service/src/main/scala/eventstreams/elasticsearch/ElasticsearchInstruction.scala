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

package eventstreams.elasticsearch

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents._
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import akka.stream.actor.{MaxInFlightRequestStrategy, RequestStrategy}
import eventstreams.JSONTools._
import eventstreams._
import eventstreams.core.actors._
import eventstreams.instructions.InstructionConstants
import eventstreams.instructions.Types._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz.\/

trait ElasticsearchInstructionSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "Instruction.Elasticsearch"

  val Built = 'Built.trace
  val StorageRequested = 'StorageScheduled.trace

}

trait ElasticsearchInstructionConstants extends InstructionConstants with ElasticsearchInstructionSysevents {
  val CfgFIdTemplate = "idTemplate"
  val CfgFIndex = "index"
  val CfgFTable = "table"
  val CfgFTTL = "ttl"
  val CfgFBranch = "branch"
}

class ElasticsearchInstruction extends BuilderFromConfig[InstructionType] with ElasticsearchInstructionConstants {
  val configId = "es"

  override def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, InstructionType] =
    for (
      _ <- props ~> CfgFIndex \/> Fail(s"Invalid $configId instruction. Missing '$CfgFIndex' value. Contents: ${Json.stringify(props)}")
    ) yield ElasticsearchInstructionActor.props(props)

}

private object ElasticsearchInstructionActor {
  def props(config: JsValue) = Props(new ElasticsearchInstructionActor(config))
}

private class ElasticsearchInstructionActor(config: JsValue)
  extends StoppableSubscribingPublisherActor
  with AtLeastOnceDeliveryActor[StoreInElasticsearch]
  with ElasticsearchInstructionConstants
  with WithSyseventPublisher
  with ActorWithResolver {

  val maxInFlight = 1000

  val elasticSearchEndpointId = ActorWithRoleId(ElasticsearchEntpointActor.id, "ep-elasticsearch")
  var endpoint: Set[ActorRef] = Set.empty

  val idTemplate = config ~> CfgFIdTemplate | "${eventId}"
  val indexTemplate = config ~> CfgFIndex | "${index}"
  val tableTemplate = config ~> CfgFTable | "default"
  val ttlTemplate = config ~> CfgFTTL | "30d"
  val branch = config ~> CfgFBranch
  val branchPath = branch.map(EventValuePath)

  override def preStart(): Unit = {
    super.preStart()
    self ! Resolve(elasticSearchEndpointId)
  }


  override def onActorResolved(actorId: ActorWithRoleId, ref: ActorRef): Unit = endpoint = Set(ref)

  override def onActorTerminated(actorId: ActorWithRoleId, ref: ActorRef): Unit = endpoint = Set.empty

  override def canDeliverDownstreamRightNow = isActive && isComponentActive && endpoint.nonEmpty

  override def getSetOfActiveEndpoints: Set[ActorRef] = endpoint

  override def execute(value: EventFrame): Option[Seq[EventFrame]] = {

    val idValue = macroReplacement(value, idTemplate)
    val indexValue = macroReplacement(value, indexTemplate)
    val tableValue = macroReplacement(value, tableTemplate)
    val ttlValue = macroReplacement(value, ttlTemplate)

    if (!idValue.isEmpty && !indexValue.isEmpty && !tableValue.isEmpty) {

      val branchToPost = (for (
        b <- branchPath;
        v <- b.extractFrame(value)
      ) yield v) | value
      val enrichedBranch = branchToPost + ('_ttl -> ttlValue)

      StorageRequested >>(
        'EntryId -> idValue,
        'Index -> indexValue,
        'Table -> tableValue,
        'TTL -> ttlValue,
        'EventId -> value.eventIdOrNA,
        'InstructionInstanceId -> uuid)

      deliverMessage(StoreInElasticsearch(self, indexValue, tableValue, idValue, Json.stringify(enrichedBranch.asJson)))
    }

    Some(List(value))

  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxInFlight) {
    override def inFlightInternally: Int = inFlightCount + pendingToDownstreamCount
  }

  override implicit val cluster: Cluster = Cluster(context.system)

  override def fullyAcknowledged(correlationId: Long, msg: Batch[StoreInElasticsearch]): Unit = {}
}

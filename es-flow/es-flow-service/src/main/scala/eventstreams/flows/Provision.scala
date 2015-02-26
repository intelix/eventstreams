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

import akka.actor.{ActorRef, Address, Deploy, Props}
import akka.remote.RemoteScope
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._

import scala.util.Random

trait FlowProvisionSysevents
  extends ComponentWithBaseSysevents {

  val FlowDeploymentRemoved = 'FlowDeploymentRemoved.trace
  val FlowDeploymentAdded = 'FlowDeploymentAdded.trace

}


private[flows] trait Provision 
  extends ActorWithComposableBehavior 
  with ActorWithClusterAwareness 
  with FlowProvisionSysevents {

  _: WithSyseventPublisher =>
  
  private val rnd = new Random()
  
  private var workersMaps: Map[String, WorkersMap] = Map()
  private var actors: Map[String, ActorRef] = Map()

  def provisionOnNodesWithRoles: Set[String]


  def destinationFor(streamKey: String, streamSeed: String): Option[ActorRef] = destinationFor(streamKey, (streamKey + ":" + streamSeed).hashCode)

  private def destinationFor(streamKey: String, hashCode: Int): Option[ActorRef] =
    addressListForStream(streamKey).hashToAddress match {
      case a if a.isEmpty => None
      case a => addressToActor(a(hashCode % a.length))
    }


  override def onTerminated(ref: ActorRef): Unit = {
    removeActor(ref)
    super.onTerminated(ref)
  }

  override def onClusterMemberUp(info: NodeInfo): Unit = {
    if (info.roles.exists(provisionOnNodesWithRoles.contains)) addFlow(info.address)
    super.onClusterMemberUp(info)
  }

  private def removeActor(ref: ActorRef) = {
    val sizeBefore = actors.size
    actors = actors.filter {
      case (k,v) if v != ref => true
      case (k,v) =>
        remapWorkers(k)
        false
    }
    val sizeNow = actors.size
    if (sizeNow != sizeBefore)
      FlowDeploymentRemoved >> ('Address -> ref.path.address.toString, 'ActiveDeployments -> sizeNow)
  }

  private def remapWorkers(address: String) = 
    workersMaps = workersMaps.map {
      case (streamKey, map) =>
        streamKey -> substitute(address, map)
    }
  
  private def substitute(address: String, inMap: WorkersMap): WorkersMap =
    WorkersMap(inMap.hashToAddress.foldLeft[Option[Array[String]]](Some(Array[String]())) {
      case (None, _) => None
      case (Some(arr), next) if next != address => Some(arr :+ next)
      case (Some(arr), next) => bestSubstituteFor(next, arr).map(arr :+ _)
    } match {
      case Some(arr) => arr
      case None => Array()
    })
  
  private def bestSubstituteFor(address: String, arr: Array[String]): Option[String] = {
    val list = allAddresses.filterNot(arr.contains) match {
      case Array() => allAddresses
      case filtered => filtered
    } 
    if (list.length == 0) 
      None 
    else 
      Some(list(rnd.nextInt() % list.length))
  }
  
  private def addFlow(address: Address) = {
    val ref = deployTo(address)
    context.watch(ref)
    actors = actors + (address.toString -> ref)
    FlowDeploymentAdded >> ('Address -> address.toString, 'ActiveDeployments -> actors.size)
  }

  private def addressToActor(address: String): Option[ActorRef] = actors.get(address)

  private def addressListForStream(streamKey: String): WorkersMap =
    workersMaps.get(streamKey) match {
      case Some(v) => v
      case _ =>
        val newMap = initialiseMapForStream(streamKey)
        workersMaps = workersMaps + (streamKey -> newMap)
        newMap
    }

  private def initialiseMapForStream(streamKey: String): WorkersMap = WorkersMap(allAddresses)

  private def allAddresses = 
    provisionOnNodesWithRoles
    .flatMap(roleToAllAvailableAddresses)
    .toArray
    .sorted

  private def deployTo(addr: Address): ActorRef = context.system.actorOf(Props[FlowProxyActor].withDeploy(Deploy(scope = RemoteScope(addr))))

  private case class WorkersMap(hashToAddress: Array[String])

}

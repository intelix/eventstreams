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
import eventstreams.{BecomePassive, BecomeActive}
import eventstreams.core.actors._

import scala.util.Random

trait FlowProvisionSysevents
  extends ComponentWithBaseSysevents {

  val FlowDeploymentRemoved = 'FlowDeploymentRemoved.info
  val FlowWorkerRemoved = 'FlowWorkerRemoved.info
  val FlowDeploymentAdded = 'FlowDeploymentAdded.info
  val FlowWorkerAdded = 'FlowWorkerAdded.info

}


private[flows] trait ProvisionLogic
  extends ActorWithComposableBehavior
  with ActorWithClusterAwareness
  with ActorWithActivePassiveBehaviors
  with FlowProvisionSysevents {

  _: WithSyseventPublisher =>

  private val rnd = new Random()

  private var seedToWorkerMap: Array[ActorRef] = Array()
  private var activeWorkers: Map[String, List[ActorRef]] = Map()

  def provisionOnNodesWithRoles: Set[String]

  def instancesPerNode: Int

  def destinationFor(streamKey: String, streamSeed: String): Option[ActorRef] = destinationFor(streamKey, (streamKey + ":" + streamSeed).hashCode)

  def totalWorkers = activeWorkers.values.foldLeft(0)((a, list) => a + list.size)

  def totalDeployments = activeWorkers.size

  def initialiseDeployment(address: String, index: Int, ref: ActorRef): Unit

  override def postStop(): Unit = {
    activeWorkers.values.flatten.foreach(context.stop)
    activeWorkers = Map()
    seedToWorkerMap = Array()
    super.postStop()
  }

  override def onTerminated(ref: ActorRef): Unit = {
    removeActor(ref)
    super.onTerminated(ref)
  }

  override def onClusterMemberUp(info: NodeInfo): Unit = {
    if (info.roles.exists(provisionOnNodesWithRoles.contains)) addFlow(info.address)
    super.onClusterMemberUp(info)
  }

  override def onClusterMemberRemoved(info: NodeInfo): Unit = {
    activeWorkers.get(info.address.toString).foreach { list =>
      activeWorkers = activeWorkers - info.address.toString
      list.foreach(removeActor)
      FlowDeploymentRemoved >>('Address -> info.address.toString, 'ActiveDeployments -> totalDeployments, 'ActiveWorkers -> totalWorkers)
    }
    super.onClusterMemberRemoved(info)
  }

  override def onBecameActive(): Unit = {
    super.onBecameActive()
    remapWorkers()
    activeWorkers.values.foreach(_.foreach(_ ! BecomeActive()))
  }

  override def onBecamePassive(): Unit = {
    super.onBecamePassive()
    activeWorkers.values.foreach(_.foreach(_ ! BecomePassive()))
  }

  private def destinationFor(streamKey: String, hashCode: Int): Option[ActorRef] =
    seedToWorkerMap match {
      case a if a.isEmpty => None
      case a =>
        Some(a(hashCode.abs % a.length))
    }

  private def isActiveWorker(ref: ActorRef) = activeWorkers.values.exists(_.contains(ref))

  private def removeActor(ref: ActorRef) =
    activeWorkers.get(ref.path.address.toString).foreach { list =>
      val addr = ref.path.address.toString
      val newList = list.filter(_ != ref)
      if (newList.isEmpty) {
        activeWorkers = activeWorkers - addr
        FlowDeploymentRemoved >>('Address -> addr, 'ActiveDeployments -> totalDeployments, 'ActiveWorkers -> totalWorkers)
      } else activeWorkers = activeWorkers + (addr -> newList)
      if (newList.size != list.size)
        FlowWorkerRemoved >>('Address -> addr, 'ActiveDeployments -> totalDeployments, 'ActiveWorkers -> totalWorkers)
      remapWorkers()
    }


  private def remapWorkers() =
    seedToWorkerMap =
      if (seedToWorkerMap.isEmpty) activeWorkers.keys.toList.sorted.flatMap { k => activeWorkers.get(k).get }.toArray
      else if (activeWorkers.size == 0) Array()
      else seedToWorkerMap.map {
        case r if isActiveWorker(r) => r
        case r => bestSubstitute()
      }


  private def bestSubstitute(): ActorRef =
    activeWorkers.values.collectFirst {
      case l if l.exists(!seedToWorkerMap.contains(_)) => l.find(!seedToWorkerMap.contains(_))
    }.flatten match {
      case None =>
        val arr = activeWorkers.values.flatten.toArray
        arr(rnd.nextInt().abs % arr.length)
      case Some(r) => r
    }

  private def addFlow(address: Address) = {
    (1 to instancesPerNode) foreach { i =>
      val ref = deployTo(address)
      context.watch(ref)
      initialiseDeployment(address.toString, i, ref)
      if (isComponentActive) ref ! BecomeActive()
      activeWorkers = activeWorkers + (address.toString -> (activeWorkers.getOrElse(address.toString, List()) :+ ref))
      FlowWorkerAdded >>('Address -> address.toString, 'Ref -> ref)
    }
    FlowDeploymentAdded >>('Address -> address.toString, 'ActiveDeployments -> totalDeployments, 'ActiveWorkers -> totalWorkers)
  }

  private def deployTo(addr: Address): ActorRef = context.system.actorOf(Props[FlowDeployableActor].withDeploy(Deploy(scope = RemoteScope(addr))))


}

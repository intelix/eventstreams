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

package eventstreams.core.components.routing

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors._
import net.ceedubs.ficus.Ficus._

import scala.collection.mutable
import scala.concurrent.duration._
import scalaz.Scalaz._


trait MessageRouterSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with SubjectSubscriptionSysevents {
  override def componentId: String = "MessageRouter"

  val ForwardedToClient = 'ForwardedToClient.trace

  val ForwardedToNode = 'ForwardedToNode.trace
  val ForwardedToLocalProviders = 'ForwardedToLocalProviders.trace

  val MessageDropped = 'MessageDropped.trace
  val MessageForwarded = 'MessageForwarded.trace

  val NewSubscription = 'NewSubscription.info

  val NewPeerSubscription = 'NewPeerSubscription.info

  val RouteAdded = 'RouteAdded.info
  val RouteRemoved = 'RouteRemoved.info

  val AddedToCache = 'AddedToCache.trace
  val RemovedFromCache = 'RemovedFromCache.trace
  val RespondedWithCached = 'RespondedWithCached.trace

  val PendingComponentRemoval = 'PendingComponentRemoval.info

}

object MessageRouterActor extends ActorObjWithCluster with MessageRouterSysevents {
  def id = "router"

  def props(implicit cluster: Cluster, config: Config) = Props(new MessageRouterActor())
}


case class ProviderState(ref: ActorRef, active: Boolean)

case class RemoveIfInactive(ref: ActorRef)

case class CachingDisabled()

class MessageRouterActor(implicit val cluster: Cluster, sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithRemoteSubscribers
  with ActorWithClusterAwareness
  with MessageRouterSysevents
  with WithSyseventPublisher {

  val providerRemoveTimeout = sysconfig.as[Option[FiniteDuration]]("eventstreams.message-router.provider-remove-timeout") | 30.seconds

  val updatesCache: mutable.Map[RemoteAddrSubj, Any] = new mutable.HashMap[RemoteAddrSubj, Any]()
  implicit val ec = context.dispatcher
  var staticRoutes: Map[ComponentKey, ProviderState] = Map[ComponentKey, ProviderState]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceAddress -> myAddress)

  def myNodeIsTarget(subj: Any): Boolean = subj match {
    case LocalSubj(_, _) => true
    case RemoteAddrSubj(role, _) => aliasToFullAddress(role) match {
      case Some(a) if a == myAddress => true
      case _ => false
    }
    case _ => true
  }

  private def subjectsForAddress(addr: String) = collectSubjects { a => aliasToFullAddress(a.address).map(_ == addr) | false }

  override def onClusterMemberUp(info: NodeInfo): Unit =
    if (info.address.toString != myAddress)
      subjectsForAddress(info.address.toString).foreach { subj =>
        NewPeerSubscription >> ('Subject -> subj)
        forwardToClusterNode(info.address.toString, Subscribe(self, subj))
      }

  override def onClusterMemberRemoved(info: NodeInfo): Unit =
    if (info.address.toString != myAddress)
      subjectsForAddress(info.address.toString).foreach { subj =>
        publishToClients(subj, Stale(_))
      }

  override def onClusterMemberUnreachable(info: NodeInfo): Unit = {
    if (info.address.toString != myAddress)
      subjectsForAddress(info.address.toString).foreach { subj =>
        publishToClients(subj, Stale(_))
      }
  }

  def forwardDownstream(subj: RemoteAddrSubj, msg: Any): Unit = {
    if (!myNodeIsTarget(subj)) {
      ForwardedToNode >> ('Subject -> subj)
      forwardToClusterNode(subj.address, msg)
    } else {
      ForwardedToLocalProviders >> ('Subject -> subj.localSubj)
      forwardToLocalProviders(subj.localSubj, msg)
    }
  }

  def forwardToLocalProviders(subj: LocalSubj, msg: Any) = {
    val component = subj.component
    staticRoutes.get(component) match {
      case Some(ProviderState(ref, true)) =>
        MessageForwarded >>('Subject -> subj, 'MessageType -> msg.getClass.getSimpleName, 'Target -> ref, 'ComponentKey -> component.key)
        ref ! msg
      case Some(ProviderState(ref, false)) =>
        MessageDropped >>('Reason -> "Inactive route", 'Target -> component)
      case None =>
        MessageDropped >>('Reason -> "Unknown route", 'Target -> component)
    }
  }

  def register(ref: ActorRef, component: ComponentKey): Unit = {
    staticRoutes += component -> ProviderState(ref, active = true)
    context.watch(ref)
    RouteAdded >>('Route -> component.key, 'Ref -> ref)
    collectSubjects { subj => subj.localSubj.component == component && myNodeIsTarget(subj) }.foreach { subj =>
      NewSubscription >> ('Subject -> subj)
      forwardDownstream(subj, Subscribe(self, subj))
    }
  }

  def remember(subj: RemoteAddrSubj, update: CacheableMessage) = if (update.canBeCached) {
    AddedToCache >> ('Subject -> subj)
    updatesCache += (subj -> update)
  } else {
    updatesCache += subj -> CachingDisabled()
  }

  def clearCacheFor(subject: RemoteAddrSubj) = {
    RemovedFromCache >> ('Subject -> subject)
    updatesCache.remove(subject)
  }

  def publishToClients(subj: Any, f: RemoteAddrSubj => Any) =
    convertSubject(subj) foreach { subj =>
      val msg = f(subj)
      msg match {
        case x: CacheableMessage => remember(subj, x)
        case _ => ()
      }
      subscribersFor(subj) foreach { setOfActors =>
        setOfActors.foreach { actor =>
          actor ! msg
          ForwardedToClient >>('Subject -> subj, 'Target -> actor)
        }
      }
    }

  def removeRoute(ref: ActorRef): Unit =
    staticRoutes = staticRoutes.filter {
      case (c, ProviderState(thatRef, false)) if ref == thatRef =>
        RouteRemoved >> ('Route -> c.key)
        collectSubjects { subj =>
          subj.localSubj.component == c && myNodeIsTarget(subj)
        } foreach { subj =>
          publishToClients(subj, Stale(_))
        }
        context.unwatch(ref) // can be removed
        false
      case _ => true
    }

  def isProviderRef(ref: ActorRef) = staticRoutes.exists { case (_, provState) => provState.ref == ref }

  override def firstSubscriber(subject: RemoteAddrSubj) = forwardDownstream(subject, Subscribe(self, subject))

  override def lastSubscriberGone(subject: RemoteAddrSubj) = {
    forwardDownstream(subject, Unsubscribe(self, subject))
    clearCacheFor(subject)
  }

  override def processSubscribeRequest(ref: ActorRef, subject: RemoteAddrSubj) = {
    super.processSubscribeRequest(ref, subject)
    updatesCache.get(subject) match {
      case Some(CachingDisabled()) =>
        forwardDownstream(subject, Subscribe(self, subject))
        RespondedWithCached >>('Subject -> subject, 'Target -> ref)
      case Some(msg) =>
        ref ! msg
        RespondedWithCached >>('Subject -> subject, 'Target -> ref)
      case _ => ()
    }
  }

  override def processUnsubscribeRequest(ref: ActorRef, subject: RemoteAddrSubj) =
    super.processUnsubscribeRequest(ref, subject)

  override def processCommand(subject: RemoteAddrSubj, replyToSubj: Option[Any], maybeData: Option[String]) = {
    super.processCommand(subject, replyToSubj, maybeData)
    forwardDownstream(subject, Command(subject, convertSubject(replyToSubj.getOrElse(None)), maybeData))
  }


  override def onTerminated(ref: ActorRef): Unit = {
    if (isProviderRef(ref)) {
      staticRoutes = staticRoutes.map {
        case (c, state) if ref == state.ref =>
          collectSubjects { subj =>
            subj.localSubj.component == c && myNodeIsTarget(subj)
          } foreach { subj =>
            publishToClients(subj, Stale(_))
          }
          c -> ProviderState(ref, active = false)
        case (c, state) => c -> state
      }
      scheduleRemoval(ref)
    }
    super.onTerminated(ref)
  }

  private def handler: Receive = {
    case Update(subj, data, cacheable) => publishToClients(subj, Update(_, data, cacheable))
    case CommandErr(subj, data) => convertSubject(subj) foreach { remoteSubj => forwardDownstream(remoteSubj, CommandErr(remoteSubj, data)) }
    case CommandOk(subj, data) => convertSubject(subj) foreach { remoteSubj => forwardDownstream(remoteSubj, CommandOk(remoteSubj, data)) }
    case Stale(subj) => publishToClients(subj, Stale(_))
    case RegisterComponent(c, ref) => register(ref, c)
    case RemoveIfInactive(ref) => removeRoute(ref)
  }

  private def scheduleRemoval(ref: ActorRef) {
    PendingComponentRemoval >>('Ref -> ref, 'Timeout -> providerRemoveTimeout)
    context.system.scheduler.scheduleOnce(providerRemoveTimeout, self, RemoveIfInactive(ref))
  }
}

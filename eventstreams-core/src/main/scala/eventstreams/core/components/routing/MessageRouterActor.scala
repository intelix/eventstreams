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

import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors._
import eventstreams.core.messages._
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.JsValue

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, DurationLong}
import scalaz._
import Scalaz._


trait MessageRouterEvents extends ComponentWithBaseEvents with BaseActorEvents with SubjectSubscriptionEvents{
  override def componentId: String = "Actor.MessageRouter"

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

object MessageRouterActor extends ActorObjWithCluster with MessageRouterEvents {
  def id = "router"

  def props(implicit cluster: Cluster, config: Config) = Props(new MessageRouterActor())
}


case class ProviderState(ref: ActorRef, active: Boolean)

case class RemoveIfInactive(ref: ActorRef)

class MessageRouterActor(implicit val cluster: Cluster, sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithRemoteSubscribers
  with ActorWithClusterAwareness
  with MessageRouterEvents
  with WithEventPublisher {

  val providerRemoveTimeout = sysconfig.as[Option[FiniteDuration]]("eventstreams.message-router.provider-remove-timeout") | 30.seconds
  
  val updatesCache: mutable.Map[RemoteSubj, Any] = new mutable.HashMap[RemoteSubj, Any]()
  implicit val ec = context.dispatcher
  var staticRoutes: Map[ComponentKey, ProviderState] = Map[ComponentKey, ProviderState]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceAddress -> myAddress)

  def myNodeIsTarget(subj: Any): Boolean = subj match {
    case LocalSubj(_, _) => true
    case RemoteSubj(addr, _) => addr == myAddress
    case _ => true
  }

  override def onClusterMemberUp(info: NodeInfo): Unit =
    if (info.address.toString != myAddress)
      collectSubjects(_.address == info.address.toString).foreach { subj =>
        NewPeerSubscription >> ('Subject -> subj)
        forwardToClusterNode(info.address.toString, Subscribe(self, subj))
      }

  override def onClusterMemberRemoved(info: NodeInfo): Unit =
    if (info.address.toString != myAddress)
      collectSubjects(_.address == info.address.toString).foreach { subj =>
        publishToClients(subj, Stale(self, _))
      }

  override def onClusterMemberUnreachable(info: NodeInfo): Unit = {
    if (info.address.toString != myAddress)
      collectSubjects(_.address == info.address.toString).foreach { subj =>
        publishToClients(subj, Stale(self, _))
      }
  }

  def forwardDownstream(subj: RemoteSubj, msg: Any): Unit = {
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
        MessageForwarded >> ('Target -> ref, 'ComponentKey -> component.key)
        ref ! msg
      case Some(ProviderState(ref, false)) =>
        MessageDropped >> ('Reason -> "Inactive route", 'Target -> component)
      case None =>
        MessageDropped >> ('Reason -> "Unknown route", 'Target -> component)
    }
  }

  def register(ref: ActorRef, component: ComponentKey): Unit = {
    staticRoutes += component -> ProviderState(ref, active = true)
    context.watch(ref)
    RouteAdded >> ('Route -> component.key, 'Ref -> ref)
    collectSubjects { subj => subj.localSubj.component == component && myNodeIsTarget(subj)}.foreach { subj =>
      NewSubscription >> ('Subject -> subj)
      forwardDownstream(subj, Subscribe(self, subj))
    }
  }

  def remember(subj: RemoteSubj, update: Any) = {
    AddedToCache >> ('Subject -> subj)
    updatesCache += (subj -> update)
  }

  def clearCacheFor(subject: RemoteSubj) = {
    RemovedFromCache >> ('Subject -> subject)
    updatesCache.remove(subject)
  }

  def publishToClients(subj: Any, f: RemoteSubj => Any) =
    convertSubject(subj) foreach { subj =>
      val msg = f(subj)
      remember(subj, msg)
      subscribersFor(subj) foreach { setOfActors =>
        setOfActors.foreach { actor =>
          actor ! msg
          ForwardedToClient >> ('Subject -> subj, 'Target -> actor)
        }
      }
    }

  def removeRoute(ref: ActorRef): Unit =
    staticRoutes = staticRoutes.filter {
      case (component, ProviderState(thatRef, false)) if ref == thatRef =>
        RouteRemoved >> ('Route -> component.key)
        collectSubjects { subj =>
          subj.localSubj.component == component && myNodeIsTarget(subj)
        } foreach { subj =>
          publishToClients(subj, Stale(self, _))
        }
        context.unwatch(ref) // can be removed
        false
      case _ => true
    }

  def isProviderRef(ref: ActorRef) = staticRoutes.exists { case (_, provState) => provState.ref == ref}

  override def firstSubscriber(subject: RemoteSubj) = forwardDownstream(subject, Subscribe(self, subject))

  override def lastSubscriberGone(subject: RemoteSubj) = {
    forwardDownstream(subject, Unsubscribe(self, subject))
    clearCacheFor(subject)
  }

  override def processSubscribeRequest(ref: ActorRef, subject: RemoteSubj) = {
    updatesCache.get(subject) foreach { msg => 
      ref ! msg
      RespondedWithCached >> ('Subject -> subject, 'Target -> ref)
    }
  }

  override def processUnsubscribeRequest(ref: ActorRef, subject: RemoteSubj) =
    super.processUnsubscribeRequest(ref, subject)

  override def processCommand(ref: ActorRef, subject: RemoteSubj, replyToSubj: Option[Any], maybeData: Option[JsValue]) =
    forwardDownstream(subject, Command(self, subject, convertSubject(replyToSubj.getOrElse(None)), maybeData))


  override def onTerminated(ref: ActorRef): Unit = {
    if (isProviderRef(ref)) {
      staticRoutes = staticRoutes.map {
        case (component, state) if ref == state.ref =>
          collectSubjects { subj =>
            subj.localSubj.component == component && myNodeIsTarget(subj)
          } foreach { subj =>
            publishToClients(subj, Stale(self, _))
          }
          component -> ProviderState(ref, active = false)
        case (component, state) => component -> state
      }
      scheduleRemoval(ref)
    }
    super.onTerminated(ref)
  }

  private def handler: Receive = {
    case Update(_, subj, data, cacheable) => publishToClients(subj, Update(self, _, data, cacheable))
    case CommandErr(_, subj, data) => convertSubject(subj) foreach { remoteSubj => forwardDownstream(remoteSubj, CommandErr(self, remoteSubj, data))}
    case CommandOk(_, subj, data) => convertSubject(subj) foreach { remoteSubj => forwardDownstream(remoteSubj, CommandOk(self, remoteSubj, data))}
    case Stale(_, subj) => publishToClients(subj, Stale(self, _))
    case RegisterComponent(component, ref) => register(ref, component)
    case RemoveIfInactive(ref) => removeRoute(ref)
  }

  private def scheduleRemoval(ref: ActorRef) {
    PendingComponentRemoval >> ('Ref -> ref, 'Timeout -> providerRemoveTimeout)
    context.system.scheduler.scheduleOnce(providerRemoveTimeout, self, RemoveIfInactive(ref))
  }
}

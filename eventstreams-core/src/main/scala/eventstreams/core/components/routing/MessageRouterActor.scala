/*
 * Copyright 2014 Intelix Pty Ltd
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
import eventstreams.core.actors._
import eventstreams.core.messages._
import play.api.libs.json.JsValue

import scala.collection.mutable
import scala.concurrent.duration.DurationLong


object MessageRouterActor extends ActorObjWithCluster {
  def id = "router"

  def props(implicit cluster: Cluster, config: Config) = Props(new MessageRouterActor())
}


case class ProviderState(ref: ActorRef, active: Boolean)

case class RemoveIfInactive(ref: ActorRef)

class MessageRouterActor(implicit val cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithRemoteSubscribers
  with ActorWithClusterAwareness {

  val updatesCache: mutable.Map[RemoteSubj, Any] = new mutable.HashMap[RemoteSubj, Any]()
  implicit val ec = context.dispatcher
  var staticRoutes: Map[ComponentKey, ProviderState] = Map[ComponentKey, ProviderState]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def myNodeIsTarget(subj: Any): Boolean = subj match {
    case LocalSubj(_, _) => true
    case RemoteSubj(addr, _) => addr == myAddress
    case _ => true
  }

  override def onClusterMemberUp(info: NodeInfo): Unit =
    if (info.address.toString != myAddress)
      collectSubjects(_.address == info.address.toString).foreach { subj =>
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
      logger.debug(s"$msg -> ${subj.address}")
      forwardToClusterNode(subj.address, msg)
    } else {
      logger.debug(s"$msg -> ${subj.localSubj}")
      forwardToLocalProviders(subj.localSubj, msg)
    }
  }

  def forwardToLocalProviders(subj: LocalSubj, msg: Any) = {
    val component = subj.component
    staticRoutes.get(component) match {
      case Some(ProviderState(ref, true)) => ref ! msg
      case Some(ProviderState(ref, false)) =>
        logger.debug(s"Route $component is inactive, message dropped")
      case None => logger.warn(s"Unknown route $component, message dropped")
    }
  }

  def register(ref: ActorRef, component: ComponentKey): Unit = {
    staticRoutes += component -> ProviderState(ref, active = true)
    context.watch(ref)
    logger.info(s"Registered new static route $component -> $ref")
    collectSubjects { subj => subj.localSubj.component == component && myNodeIsTarget(subj)}.foreach { subj =>
      logger.info(s"Subscribing to $subj with downstream component")
      forwardDownstream(subj, Subscribe(self, subj))
    }
  }

  def remember(subj: RemoteSubj, update: Any) = {
    logger.debug(s"Caching update for $subj")
    updatesCache += (subj -> update)
  }

  def clearCacheFor(subject: RemoteSubj) = {
    logger.debug(s"Removing from cache update for $subject")
    updatesCache.remove(subject)
  }

  def publishToClients(subj: Any, f: RemoteSubj => Any) =
    convertSubject(subj) foreach { subj =>
      val msg = f(subj)
      remember(subj, msg)
      subscribersFor(subj) foreach { setOfActors =>
        setOfActors.foreach { actor =>
          actor ! msg
          logger.debug(s"s2c: $msg -> $actor")
        }
      }
    }

  def removeRoute(ref: ActorRef): Unit =
    staticRoutes = staticRoutes.filter {
      case (component, ProviderState(thatRef, false)) if ref == thatRef =>
        logger.info(s"Removing route: $component")
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
    updatesCache.get(subject) foreach (ref ! _)
    logger.debug(s"Responded with cached update for $subject -> $ref")
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
    logger.info(s"Actor $ref terminated, pending removal in 30 sec")
    context.system.scheduler.scheduleOnce(30.seconds, self, RemoveIfInactive(ref))
  }
}

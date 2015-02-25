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

package eventstreams.core.actors

import akka.actor.{ActorPath, ActorRef, ActorSelection}
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

case class ActorWithRoleId(actorId: String, role: String)

case class Resolved(actorId: ActorWithRoleId, ref: ActorRef)

case class Resolve(actorId: ActorWithRoleId)

trait ActorWithResolverSysevents extends ComponentWithBaseSysevents {

  val ResolvingActor = 'ResolvingActor.trace
  val ResolvedActor = 'ResolvedActor.trace

}


trait ActorWithResolver extends ActorWithClusterAwareness with ActorWithResolverSysevents {

  _:WithSyseventPublisher =>
  
  implicit val ec = context.dispatcher

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  private var resolved: Map[ActorWithRoleId, ActorRef] = Map.empty

  def onActorResolved(actorId: ActorWithRoleId, ref: ActorRef) = {}
  def onActorTerminated(actorId: ActorWithRoleId, ref: ActorRef) = {}

  def getResolvedActorRef(actorId: ActorWithRoleId) = resolved.get(actorId)
  
  override def onTerminated(ref: ActorRef): Unit = {
    resolved = resolved.filter {
      case (k,v) if v == ref =>
        onActorTerminated(k, v)
        initiate(k)
        false
      case _ => true
    }
    super.onTerminated(ref)
  }

  private def initiate(m: ActorWithRoleId) = roleToAddress(m.role) match {
    case None => 
      context.system.scheduler.scheduleOnce(4.seconds, self, Resolve(m))
    case Some(a) =>
      selectionFor(a, m.actorId).resolveOne(5.seconds).onComplete {
      case Success(ref) =>
        context.watch(ref)
        resolved = resolved + (m -> ref)
        self ! Resolved(m, ref)
      case Failure(ref) => context.system.scheduler.scheduleOnce(2.seconds, self, Resolve(m))
    }
  }

  
  private def handler: Receive = {
    case Resolve(req) =>
      ResolvingActor >> ('ActorId -> req.actorId, 'NodeRole -> req.role)
      initiate(req)
    case Resolved(id, ref) =>
      ResolvedActor >> ('ActorId -> id.actorId, 'NodeRole -> id.role, 'Ref -> ref.path)
      onActorResolved(id, ref)
  }

}

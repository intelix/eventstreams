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

import akka.actor.Actor
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.{BecomeActive, BecomePassive, NowProvider}

import scalaz.Scalaz._


trait StateChangeSysevents extends ComponentWithBaseSysevents {
  val BecomingActive = 'BecomingActive.info
  val BecomingPassive = 'BecomingPassive.info
}

trait ActorWithActivePassiveBehaviors extends ActorWithComposableBehavior with NowProvider with StateChangeSysevents with WithSyseventPublisher {

  private var requestedState: Option[RequestedState] = None
  private var date: Option[Long] = None

  def lastRequestedState = requestedState

  def isComponentActive = requestedState match {
    case Some(Active()) => true
    case _ => false
  }

  def isComponentPassive = !isComponentActive

  def onBecameActive(): Unit = {}

  def onBecamePassive(): Unit = {}

  def millisTimeSinceStateChange = date.map(now - _) | -1

  def prettyTimeSinceStateChange = date match {
    case None => "never"
    case Some(l) => prettyTimeFormat(l)
  }

  final def becomeActive() = {
    BecomingActive >>()
    requestedState = Some(Active())
    date = Some(now)
    onBecameActive()
  }
  
  final def becomePassive() = {
    BecomingPassive >>()
    requestedState = Some(Passive())
    date = Some(now)
    onBecamePassive()
  }
  
  override def commonBehavior: Actor.Receive = handlePipelineStateChanges orElse super.commonBehavior

  private def handlePipelineStateChanges: Actor.Receive = {
    case BecomeActive() => becomeActive()
    case BecomePassive() => becomePassive()
  }

  sealed trait RequestedState

  case class Active() extends RequestedState

  case class Passive() extends RequestedState

}

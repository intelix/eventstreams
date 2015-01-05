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

package eventstreams.core.actors

import akka.actor.Actor
import core.events.EventOps.symbolToEventOps
import core.events.{CtxComponent, WithEventPublisher}
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.{BecomePassive, BecomeActive, NowProvider}

import scalaz.Scalaz._
import scalaz._


trait StateChangeEvents extends ComponentWithBaseEvents {
  val BecomingActive = 'BecomingActive.info
  val BecomingPassive = 'BecomingPassive.info
}

trait PipelineWithStatesActor extends ActorWithComposableBehavior with NowProvider with StateChangeEvents with WithEventPublisher {

  private var requestedState: Option[RequestedState] = None
  private var date: Option[Long] = None

  def lastRequestedState = requestedState

  def isComponentActive = requestedState match {
    case Some(Active()) => true
    case _ => false
  }

  def isComponentPassive = !isComponentActive

  def becomeActive(): Unit = {}

  def becomePassive(): Unit = {}

  def millisTimeSinceStateChange = date.map(now - _) | -1

  def prettyTimeSinceStateChange = date match {
    case None => "never"
    case Some(l) => prettyTimeFormat(l)
  }

  override def commonBehavior: Actor.Receive = handlePipelineStateChanges orElse super.commonBehavior

  private def handlePipelineStateChanges: Actor.Receive = {
    case BecomeActive() =>
      BecomingActive >>()
      requestedState = Some(Active())
      date = Some(now)
      becomeActive()
    case BecomePassive() =>
      BecomingPassive >>()
      requestedState = Some(Passive())
      date = Some(now)
      becomePassive()
  }

  sealed trait RequestedState

  case class Active() extends RequestedState

  case class Passive() extends RequestedState

}

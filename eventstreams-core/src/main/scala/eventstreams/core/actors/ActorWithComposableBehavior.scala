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

import akka.actor.{ActorRef, Terminated}
import com.typesafe.scalalogging.StrictLogging
import core.events.EventOps.{stringToEventOps, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents

trait BaseActorEvents extends ComponentWithBaseEvents {
  val WatchedActorTerminated = 'WatchedActorTerminated.info
  val BehaviorSwitch = 'BehaviorSwitch.info
  val PostStop = "Lifecycle.PostStop".info
  val PreStart = "Lifecycle.PreStart".info
  val PreRestart = "Lifecycle.PreRestart".info
  val PostRestart = "Lifecycle.PostRestart".info
}


trait ActorWithComposableBehavior extends ActorUtils with StrictLogging with BaseActorEvents with WithEventPublisher {

  def onTerminated(ref:ActorRef) = {
    WatchedActorTerminated >> ('Actor -> ref)
  }


  @throws[Exception](classOf[Exception])
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    PreRestart >> ('Reason -> reason.getMessage, 'Message -> message)
    super.preRestart(reason, message)
  }


  @throws[Exception](classOf[Exception])
  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    PostRestart >> ('Reason -> reason.getMessage)
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    PreStart >> ()
    super.preStart()
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    PostStop >> ()
  }

  def commonBehavior: Receive = {
    case Terminated(ref) => onTerminated(ref)
    case msg: Loggable => logger.info(String.valueOf(msg))
  }

  final def switchToCustomBehavior(customBehavior: Receive, bid: Option[String] = None) = {
    BehaviorSwitch >> ('BehaviorId -> bid)
    context.become(customBehavior orElse commonBehavior)
  }

  final def switchToCommonBehavior() = {
    BehaviorSwitch >> ('BehaviorId -> "common")
    context.become(commonBehavior)
  }

  def beforeMessage() = {}
  def afterMessage() = {}

  def wrapped(c: scala.PartialFunction[scala.Any, scala.Unit]): Receive = {
    case x =>
      beforeMessage()
      if (c.isDefinedAt(x)) c(x)
      afterMessage()
  }

  final override def receive: Receive = wrapped(commonBehavior)

}

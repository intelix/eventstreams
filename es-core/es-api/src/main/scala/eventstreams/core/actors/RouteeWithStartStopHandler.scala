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

import akka.actor._
import eventstreams.{BecomeActive, BecomePassive, Fail, OK}
import play.api.libs.json._

import scalaz.{-\/, \/-}


trait RouteeWithStartStopHandler
  extends RouteeActor with ActorWithActivePassiveBehaviors {

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          self ! BecomePassive()
          OK()
        case _ =>
          Fail("Already stopped")
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          Fail("Already started")
        case _ =>
          self ! BecomeActive()
          OK()
      }
  }

}

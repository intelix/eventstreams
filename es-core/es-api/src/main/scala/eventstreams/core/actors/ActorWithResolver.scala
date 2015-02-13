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

import akka.actor.{ActorRef, ActorSelection}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

trait ActorWithResolver extends ActorWithComposableBehavior {

  implicit val ec = context.dispatcher

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def onPathResolved(path: ActorSelection, ref: ActorRef)

  private def handler: Receive = {
    case RetryResolution(path) => path.resolveOne(5.seconds).onComplete {
      case Success(ref) => self ! Resolved(path, ref)
      case Failure(ref) => context.system.scheduler.scheduleOnce(2.seconds, self, RetryResolution(path))
    }
    case Resolved(path, ref) => onPathResolved(path, ref)
  }

  case class Resolved(path: ActorSelection, ref: ActorRef)

  case class RetryResolution(path: ActorSelection)

}

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

import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import eventstreams.core.Stop

trait ShutdownablePublisherActor[T] extends ActorPublisher[T] with ActorWithComposableBehavior {

  override def commonBehavior: Receive = handlePublisherShutdown orElse super.commonBehavior

  private def stop(reason: Option[String]) = {
    logger.info(s"Shutting down publisher, reason given: $reason")
    onComplete()
    context.stop(self)
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    logger.debug("!>>>> post stop!")
  }

  private def handlePublisherShutdown : Receive = {
    case Cancel => stop(Some("Cancelled"))
    case Stop(reason) => stop(reason)
  }

}

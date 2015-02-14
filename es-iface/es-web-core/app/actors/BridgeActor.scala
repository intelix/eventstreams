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

package actors

import akka.actor.{ActorRef, ActorRefFactory, Props}
import eventstreams.core.actors.{ActorObj, ActorWithComposableBehavior}


object BridgeActor extends ActorObj {
  override def id: String = "router"
  def props(ref: ActorRef) =  Props(new BridgeActor(ref))
  def start(ref: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(props(ref), id)
}

class BridgeActor(ref: ActorRef) extends ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  private def handler: Receive = {
    case m => ref.forward(m)
  }

  override def componentId: String = "Private.Bridge"
}

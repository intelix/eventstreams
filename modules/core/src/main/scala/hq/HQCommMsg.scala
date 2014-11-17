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

package hq

import akka.actor.ActorRef
import common.actors.ActorUtils
import play.api.libs.json.JsValue

trait HQCommMsg[T] {
  val sourceRef: ActorRef
  val subj: T
}

trait Subj

case class TopicKey(key: String)

case class ComponentKey(key: String) {
  def /(s: String) = ComponentKey(key + "/" + s)
  def toActorId = key.replaceAll("""[\W]""", "_").replaceAll("__", "_")
}

case class LocalSubj(component: ComponentKey, topic: TopicKey) extends Subj

case class RemoteSubj(address: String, localSubj: LocalSubj) extends Subj


case class Subscribe(sourceRef: ActorRef, subj: Any) extends HQCommMsg[Any]

case class Unsubscribe(sourceRef: ActorRef, subj: Any) extends HQCommMsg[Any]

case class Command(sourceRef: ActorRef, subj: Any, replyToSubj: Option[Any], data: Option[JsValue] = None) extends HQCommMsg[Any]

case class Update(sourceRef: ActorRef, subj: Any, data: JsValue, canBeCached: Boolean = true) extends HQCommMsg[Any]

case class CommandOk(sourceRef: ActorRef, subj: Any, data: JsValue) extends HQCommMsg[Any]
case class CommandErr(sourceRef: ActorRef, subj: Any, data: JsValue) extends HQCommMsg[Any]


case class Stale(sourceRef: ActorRef, subj: Any) extends HQCommMsg[Any]

case class RegisterComponent(component: ComponentKey, ref: ActorRef)
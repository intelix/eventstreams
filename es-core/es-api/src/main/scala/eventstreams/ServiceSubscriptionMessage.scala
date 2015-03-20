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

package eventstreams

import akka.actor.ActorRef

trait ServiceSubscriptionMessage[T] extends CommMessage {
  val subj: T
}

trait CacheableMessage {
  val canBeCached: Boolean
}

trait Subj

case class TopicKey(key: String)

object TopicWithPrefix {
  def unapply(t: TopicKey): Option[(String, String)] =
    t.key.indexOf(":") match {
      case i if i < 0 => None
      case i => Some(t.key.substring(0, i), t.key.substring(i + 1))
    }

}

case class ComponentKey(key: String) {
  def /(s: String) = ComponentKey(key + "/" + s)

  def toActorId = key.replaceAll( """[\W]""", "_").replaceAll("__", "_")
}

case class LocalSubj(component: ComponentKey, topic: TopicKey) extends Subj {
  override def toString: String = component.key + "#" + topic.key
}

case class RemoteAddrSubj(address: String, localSubj: LocalSubj) extends Subj {
  override def toString: String = localSubj + "@" + address
}

case class Subscribe(sourceRef: ActorRef, subj: Any) extends ServiceSubscriptionMessage[Any]

case class Unsubscribe(sourceRef: ActorRef, subj: Any) extends ServiceSubscriptionMessage[Any]

case class Command(subj: Any, replyToSubj: Option[Any], data: Option[String] = None) extends ServiceSubscriptionMessage[Any]

case class Update(subj: Any, data: String, override val canBeCached: Boolean = true) extends ServiceSubscriptionMessage[Any] with CacheableMessage

case class CommandOk(subj: Any, data: String) extends ServiceSubscriptionMessage[Any]

case class CommandErr(subj: Any, data: String) extends ServiceSubscriptionMessage[Any]


case class Stale(subj: Any) extends ServiceSubscriptionMessage[Any] with CacheableMessage {
  override val canBeCached: Boolean = true
}

case class RegisterComponent(component: ComponentKey, ref: ActorRef)
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

package common.actors

import common.NowProvider
import hq.TopicKey

import scala.collection.mutable
import scala.language.implicitConversions

trait ActorWithPeriodicalBroadcasting extends ActorWithTicks with NowProvider {

  type Key = TopicKey
  type PayloadGenerator = (() => Any)
  type PayloadBroadcaster = (Any => Unit)
  type AutoBroadcast = (Key, Int, PayloadGenerator, PayloadBroadcaster)

  private var updatesCache = mutable.Map[Key, Any]()
  private var broadcastTimes = mutable.Map[Key, Long]()

  def updateBroadcastCache(key: Key, value: Any): Option[Any] = {
    updatesCache.get(key) match {
      case Some(v) if v.equals(value) => None
      case _ =>
        updatesCache += key -> value
        Some(value)
    }
  }

  def autoBroadcast: List[AutoBroadcast]

  implicit def string2topic(s: String): Key = TopicKey(s)

  override def processTick(): Unit = {
    autoBroadcast.foreach {
      case (key, interval, generator, publisher) => if (isTimeToBroadcast(key, interval)) updateBroadcastCache(key, generator()) foreach { x =>
        logger.debug(s"Broadcasted $key -> $x")
        publisher(x)
      }
    }
    super.processTick()
  }

  private def updateTimeOfBroadcast(key: Key) = broadcastTimes += key -> now

  private def isTimeToBroadcast(key: Key, interval: Int): Boolean = broadcastTimes get key match {
    case Some(x) if now - x < interval * 1000 => false
    case _ =>
      updateTimeOfBroadcast(key)
      true
  }
}

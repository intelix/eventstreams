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

import akka.actor.ActorRef
import eventstreams.NowProvider

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

case class LastMessage(ref: ActorRef, ts: Long, lastId: Option[Long])

trait ActorWithDupTracking extends ActorWithTicks with NowProvider {

  private val lastMessageTrackingMap = mutable.Map[ActorRef, LastMessage]()
  private var maintenanceTs = 0L

  def isDup(ref: ActorRef, id: Long): Boolean =
    lastMessageTrackingMap.getOrElse(ref, LastMessage(ref, 0, None)) match {
      case LastMessage(_, _, Some(lastId)) if lastId >= id => true
      case e =>
        lastMessageTrackingMap += ref -> e.copy(ts = now, lastId = Some(id))
        false
    }


  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    if (now - maintenanceTs > 30000) {
      lastMessageTrackingMap.collect {
        case (a, m) if now - m.ts > 1.hour.toMillis => a
      } foreach lastMessageTrackingMap.remove
      maintenanceTs = now
    }
  }
}

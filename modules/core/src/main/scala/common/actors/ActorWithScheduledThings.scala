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

import java.util.UUID

import common.NowProvider

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scalaz.std.set

trait ActorWithScheduledThings extends ActorWithTicks with NowProvider {

  type ScheduledThing = () => Unit
  val highPriorityThings = mutable.Map[String, ScheduledThing]()
  val normalPriorityThings = mutable.Map[String, ScheduledThing]()
  val lowPriorityThings = mutable.Map[String, ScheduledThing]()
  var lowPriorityTimer: Long = 0
  var normalPriorityTimer: Long = 0

  def lowPriorityInterval = 3.seconds

  def normalPriorityInterval = 1.seconds

  def scheduleHighP(thing: ScheduledThing, uniqueKey: String = UUID.randomUUID().toString): Unit = highPriorityThings.put(uniqueKey, thing)

  def scheduleNormalP(thing: ScheduledThing, uniqueKey: String = UUID.randomUUID().toString): Unit = normalPriorityThings.put(uniqueKey, thing)

  def scheduleLowP(thing: ScheduledThing, uniqueKey: String = UUID.randomUUID().toString): Unit = lowPriorityThings.put(uniqueKey, thing)

  override def processTick() = {
    val timeNow = now
    if (timeNow - normalPriorityTimer >= normalPriorityInterval.toMillis) {
      execute(normalPriorityThings)
      normalPriorityTimer = timeNow
    }
    if (timeNow - lowPriorityTimer >= lowPriorityInterval.toMillis) {
      execute(lowPriorityThings)
      normalPriorityTimer = timeNow
    }
    super.processTick()
  }


  override def afterMessage(): Unit = {
    super.afterMessage()
    execute(highPriorityThings)
  }

  private def execute(map: mutable.Map[String, ScheduledThing]): Unit =
    if (map.nonEmpty) {
      map.values.foreach(_())
      map.clear()
    }


}

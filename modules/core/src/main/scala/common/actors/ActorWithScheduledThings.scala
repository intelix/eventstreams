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

import scala.collection.mutable
import scala.concurrent.duration.DurationInt

trait ActorWithScheduledThings extends ActorWithTicks with NowProvider {

  type ScheduledThing = () => Unit
  val highPriorityThings = mutable.Set[ScheduledThing]()
  val normalPriorityThings = mutable.Set[ScheduledThing]()
  val lowPriorityThings = mutable.Set[ScheduledThing]()
  var lowPriorityTimer: Long = 0
  var normalPriorityTimer: Long = 0

  def lowPriorityInterval = 3.seconds

  def normalPriorityInterval = 1.seconds

  def scheduleHighP(thing: ScheduledThing): Unit = highPriorityThings.add(thing)

  def scheduleNormalP(thing: ScheduledThing): Unit = normalPriorityThings.add(thing)

  def scheduleLowP(thing: ScheduledThing): Unit = lowPriorityThings.add(thing)

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

  private def execute(set: mutable.Set[ScheduledThing]): Unit =
    if (set.nonEmpty) {
      set.foreach(_())
      set.clear()
    }


}

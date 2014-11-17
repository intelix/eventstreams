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

import scala.concurrent.duration.DurationLong

trait ActorWithTicks extends ActorWithComposableBehavior {

  implicit private val ec = context.dispatcher

  override def commonBehavior: Receive = handleTicks orElse super.commonBehavior

  def tickInterval = 1.second

  override def preStart(): Unit = {
    scheduleTick()
    super.preStart()
  }

  def internalProcessTick(): Unit = {}
  def processTick(): Unit = {}


  private def scheduleTick() = this.context.system.scheduler.scheduleOnce(tickInterval, self, Tick())(context.dispatcher)

  private def handleTicks: Receive = {
    case Tick() =>
      internalProcessTick()
      processTick()
      scheduleTick()
  }

  private case class Tick()


}

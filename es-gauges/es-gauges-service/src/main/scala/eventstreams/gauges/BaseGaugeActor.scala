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

package eventstreams.gauges

import eventstreams.core.actors.{ActorWithTicks, RouteeActor}
import eventstreams.signals.SignalEventFrame
import eventstreams.{ComponentKey, NowProvider, TopicKey}
import play.api.libs.json.JsValue

trait BaseGaugeActor
  extends RouteeActor
  with ActorWithTicks with NowProvider {

  self: MetricAccounting =>

  var lastDataValue: Option[JsValue] = None
  var lastLevel: Int = 0
  var lastPublishAt: Option[Long] = None

  var publishIntervalMs = 3000

  private val T_DATA = TopicKey("data")

  override def componentId: String = "Metric." + signalKey.toMetricName

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def onSubscribe: SubscribeHandler = {
    case T_DATA => T_DATA !!* lastDataValue
  }

  override def processTick(): Unit = {
    super.processTick()
    lastPublishAt = lastPublishAt match {
      case Some(t) if (!tsOfLastSignal.isDefined || tsOfLastSignal.get < t) && (now - t < publishIntervalMs)  => lastPublishAt
      case _ =>
        val newData = toData
        if (newData != lastDataValue) {
          lastDataValue = newData
          T_DATA !!* lastDataValue
        }
        lastLevel = currentLevel match {
          case newLevel if newLevel != lastLevel =>
            context.parent ! LevelUpdateNotification(signalKey, newLevel)
            newLevel
          case _ => lastLevel
        }
        Some(now)
    }
  }

  private def handleSignalUpdate(s: SignalEventFrame) = if (checkAndMarkLatestSignal(s)) update(s)

  private def handler: Receive = {
    case e: SignalEventFrame => handleSignalUpdate(e)
  }
}

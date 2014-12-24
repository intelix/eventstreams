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

package eventstreams.engine.gates

import eventstreams.core.NowProvider
import eventstreams.core.actors.ActorWithTicks
import eventstreams.engine.signals.Signal

import scala.collection.mutable

trait WithSignalAccounting extends ActorWithTicks with NowProvider {

  private val buckets = mutable.Map[Long, Int]()
  private var accountedCounter: Long = 0
  private var totalCounter: Long = 0

  def signalAccountingPeriodSec: Int = 0

  def accountedSignalsCount = accountedCounter

  def totalSignalsCount = accountedCounter

  def resetSignalAccounting() = {
    buckets.clear()
    accountedCounter = 0
    totalCounter = 0
  }

  def accountSignal(s: Signal) = {
    val bucket = bucketByTs(s.ts)
    val validBucket = currentBucket - signalAccountingPeriodSec + 1
    if (bucket >= validBucket) {
      buckets += bucket -> (buckets.getOrElse(bucket, 0) + 1)
      accountedCounter = accountedCounter + 1
      totalCounter = totalCounter + 1
    }
  }

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    val validBucket = currentBucket - signalAccountingPeriodSec + 1
    buckets.collect { case (k, v) if k < validBucket => (k, v)} foreach {
      case (k, v) =>
        buckets.remove(k)
        accountedCounter = accountedCounter - v
    }
  }

  private def currentBucket: Long = bucketByTs(now)

  private def bucketByTs(ts: Long): Long = ts / 1000
}

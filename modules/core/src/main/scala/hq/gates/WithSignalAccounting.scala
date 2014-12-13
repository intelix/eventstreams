package hq.gates

import common.NowProvider
import common.actors.ActorWithTicks
import hq.signals.Signal

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

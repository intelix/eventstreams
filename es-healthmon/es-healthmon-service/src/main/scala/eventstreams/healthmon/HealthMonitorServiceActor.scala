package eventstreams.healthmon

import java.util

import akka.actor.ActorRef
import akka.cluster.Cluster
import com.codahale.metrics.{Histogram, Meter, Timer}
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents._
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.core.metrics.{EventstreamsSensorRegistry, SimpleStateRegistry}
import eventstreams.gates.GateState
import eventstreams.signals._
import eventstreams.{EventFrame, WithCHMetrics}
import net.ceedubs.ficus.Ficus._

import scala.collection.JavaConversions._
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scalaz.Scalaz._

trait HealthMonitorServiceSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "HealthMonitor.Service"

  val ConnectedToGate = 'ConnectedToGate.info
  val DisconnectedFromGate = 'DisconnectedFromGate.warn


}

object HealthMonitorServiceConstants extends HealthMonitorServiceSysevents {
  val id = "healthmon"
}


class HealthMonitorServiceActor(sysconfig: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ReconnectingActor
  with AtLeastOnceDeliveryActor[EventFrame]
  with ActorWithGateStateMonitoring
  with WithCHMetrics
  with ActorWithInstrumentationEnabled
  with HealthMonitorServiceSysevents
  with WithSyseventPublisher {

  private val address = sysconfig.as[Option[String]]("eventstreams.healthmon.target-gate")
  private var eventsCountInSingleBatch = 0

  lazy val reportIntervalMs = sysconfig.as[Option[FiniteDuration]]("eventstreams.healthmon.gate-check-interval").map(_.toMillis) | 1000

  override def gateStateCheckInterval: FiniteDuration = sysconfig.as[Option[FiniteDuration]]("eventstreams.healthmon.gate-check-interval") | super.gateStateCheckInterval

  override def connectionEndpoint: Option[String] = address

  override def onGateStateChanged(state: GateState): Unit = {
    deliverIfPossible()
    super.onGateStateChanged(state)
  }

  override def canDeliverDownstreamRightNow = connected && isGateOpen

  override def getSetOfActiveEndpoints: Set[ActorRef] = remoteActorRef.map(Set(_)).getOrElse(Set())

  override def preStart(): Unit = {
    super.preStart()
    addTickCallback(report, reportIntervalMs)
    initiateReconnect()
  }

  override def onConnectedToEndpoint(): Unit = {
    ConnectedToGate >>()
    super.onConnectedToEndpoint()
    startGateStateMonitoring()
  }

  override def onDisconnectedFromEndpoint(): Unit = {
    DisconnectedFromGate >>()
    super.onDisconnectedFromEndpoint()
    stopGateStateMonitoring()
    initiateReconnect()
  }

  private def deliver(s: SignalKey, mId: String, strVal: Option[String], numVal: Option[Double], mType: SigMetricType, unit: Option[String] = None) =
    deliverMessage(new SignalEventFrame(
      "health",
      uuid,
      location = s.location,
      system = s.system,
      comp = s.component,
      metric = Some(s.metric + "." + mId),
      metricType = Some(mType),
      timestamp = Some(now),
      ttlMs = Some(reportIntervalMs),
      unit = unit,
      strValue = strVal,
      numValue = numVal
    ))


  private def reportHistograms(active: Set[String], map: util.SortedMap[String, Histogram]) =
    map.foreach {
      case (k, v) =>
        SignalKeyBuilder(k).foreach { s =>
          deliver(s, "Mean", None, Some(v.getSnapshot.getMean), SigMetricTypeGauge())
          deliver(s, "Median", None, Some(v.getSnapshot.getMedian), SigMetricTypeGauge())
          deliver(s, "75th", None, Some(v.getSnapshot.get75thPercentile()), SigMetricTypeGauge())
          deliver(s, "95th", None, Some(v.getSnapshot.get95thPercentile()), SigMetricTypeGauge())
          deliver(s, "99th", None, Some(v.getSnapshot.get99thPercentile()), SigMetricTypeGauge())
        }
    }

  private def reportTimers(active: Set[String], map: util.SortedMap[String, Timer]) =
    map.foreach {
      case (k, v) =>
        SignalKeyBuilder(k).foreach { s =>
          deliver(s, "Mean", None, Some(v.getSnapshot.getMean / 1000000), SigMetricTypeTiming(), Some("ms"))
          deliver(s, "Median", None, Some(v.getSnapshot.getMedian / 1000000), SigMetricTypeTiming(), Some("ms"))
          deliver(s, "75th", None, Some(v.getSnapshot.get75thPercentile() / 1000000), SigMetricTypeTiming(), Some("ms"))
          deliver(s, "95th", None, Some(v.getSnapshot.get95thPercentile() / 1000000), SigMetricTypeTiming(), Some("ms"))
          deliver(s, "99th", None, Some(v.getSnapshot.get99thPercentile() / 1000000), SigMetricTypeTiming(), Some("ms"))
        }
    }

  private def reportMeters(active: Set[String], map: util.SortedMap[String, Meter]) =
    map.foreach {
      case (k, v) =>
        SignalKeyBuilder(k).foreach { s =>
          deliver(s, "Mean Rate", None, Some(v.getMeanRate), SigMetricTypeGauge())
          deliver(s, "1m Rate", None, Some(v.getOneMinuteRate), SigMetricTypeGauge())
          deliver(s, "5m Rate", None, Some(v.getFiveMinuteRate), SigMetricTypeGauge())
          deliver(s, "15m rate", None, Some(v.getFifteenMinuteRate), SigMetricTypeGauge())
        }
    }

  private def reportStates(active: Set[String], map: TrieMap[String, String]) =
    map.foreach {
      case (k, v) =>
        SignalKeyBuilder(k).foreach { s =>
          deliver(s, "State", Some(v), None, SigMetricTypeState())
        }
    }

  def report(): Unit = {
    if (canDeliverDownstreamRightNow && (eventsCountInSingleBatch < 1 || inFlightCount < eventsCountInSingleBatch * 10)) {
      val before = inFlightCount
      val activeSensors = EventstreamsSensorRegistry.sharedSensors.keys.toSet
//      reportHistograms(activeSensors, metricRegistry.getHistograms)
//      reportMeters(activeSensors, metricRegistry.getMeters)
//      reportTimers(activeSensors, metricRegistry.getTimers)
      reportStates(activeSensors, SimpleStateRegistry.m.snapshot())
      eventsCountInSingleBatch = inFlightCount - before
    }
  }

}

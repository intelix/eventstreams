package eventstreams.healthmon

import akka.actor.ActorRef
import akka.cluster.Cluster
import com.codahale.metrics.Slf4jReporter
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents._
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.core.metrics.SimpleStateRegistry
import eventstreams.gates.GateState
import eventstreams.{EventFrame, WithCHMetrics}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration
import scalaz.Scalaz._

import scala.collection.JavaConversions._

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
  with WithSyseventPublisher  {

  private val address = sysconfig.as[Option[String]]("eventstreams.healthmon.target-gate")

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
    addTickCallback(report, 2000)
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

  val r = Slf4jReporter.forRegistry(metricRegistry).build()

  def report(): Unit = {

//    r.report()
//    r.report(
//      metricRegistry.getGauges,
//      metricRegistry.getCounters,
//      metricRegistry.getHistograms,
//      metricRegistry.getMeters,
//      metricRegistry.getTimers
//    )

//    metricRegistry.getHistograms.foreach{
//      case (k,v) => println(s"!>>>>> H: $k mean: ${v.getSnapshot.getMean} count: ${v.getCount}")
//    }
//    metricRegistry.getTimers.foreach{
//      case (k,v) => println(s"!>>>>> T: $k mean: ${v.getSnapshot.getMean} count: ${v.getCount} 1m: ${v.getOneMinuteRate}")
//    }
//
//    println("!>>>> Reported")
/*
    SimpleStateRegistry.m.snapshot().foreach {
      case (k,v) => println(s"!>>>>> S: $k state: ${v}")
    }
*/
  }

}

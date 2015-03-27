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

import akka.actor.{ActorRef, Terminated}
import com.typesafe.scalalogging.StrictLogging
import core.sysevents.SyseventOps.{stringToSyseventOps, symbolToSyseventOps}
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.NowProvider
import eventstreams.core.metrics.MetricGroups.ActorMetricGroup
import eventstreams.core.metrics.Metrics._
import eventstreams.core.metrics.{MeterSensor, TimerSensor}

trait BaseActorSysevents extends ComponentWithBaseSysevents {
  val WatchedActorTerminated = 'WatchedActorTerminated.info
  val BehaviorSwitch = 'BehaviorSwitch.info
  val PostStop = "Lifecycle.PostStop".info
  val PreStart = "Lifecycle.PreStart".info
  val PreRestart = "Lifecycle.PreRestart".info
  val PostRestart = "Lifecycle.PostRestart".info
}


trait ActorWithComposableBehavior extends ActorUtils with WithInstrumentationHooks with StrictLogging with BaseActorSysevents with WithSyseventPublisher with NowProvider {

  private lazy val MessageProcessingTimer = timerSensor(ActorMetricGroup, ProcessingTime)
  private lazy val ArrivalRateMeter = meterSensor(ActorMetricGroup, ArrivalRate)
  private lazy val FailureRateMeter = meterSensor(ActorMetricGroup, FailureRate)

  def onTerminated(ref: ActorRef) = {
    WatchedActorTerminated >> ('Actor -> ref)
  }


  @throws[Exception](classOf[Exception])
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    PreRestart >>('Reason -> reason.getMessage, 'Message -> message, 'ActorInstance -> self.path.toSerializationFormat)
    super.preRestart(reason, message)
  }


  @throws[Exception](classOf[Exception])
  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    PostRestart >>('Reason -> reason.getMessage, 'ActorInstance -> self.path.toSerializationFormat)
  }

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    PreStart >>('Path -> self.path, 'ActorInstance -> self.path.toSerializationFormat)
    super.preStart()
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    PostStop >> ('ActorInstance -> self.path.toSerializationFormat)
  }

  def commonBehavior: Receive = {
    case Terminated(ref) => onTerminated(ref)
    case msg: Loggable => logger.info(String.valueOf(msg))
  }

  final def switchToCustomBehavior(customBehavior: Receive, bid: Option[String] = None) = {
    BehaviorSwitch >> ('BehaviorId -> bid)
    context.become(customBehavior orElse commonBehavior)
  }

  final def switchToCommonBehavior() = {
    BehaviorSwitch >> ('BehaviorId -> "common")
    context.become(commonBehavior)
  }

  def beforeMessage() = {}

  def afterMessage() = {}

  def wrapped(c: scala.PartialFunction[scala.Any, scala.Unit]): Receive = {
    case x =>
      val startStamp = System.nanoTime()

      var ArrivalRateMeterByType: Option[MeterSensor] = None
      var MessageProcessingTimerByType: Option[TimerSensor] = None

      if (!x.getClass.isArray) {
        val simpleName = x.getClass.getSimpleName
        ArrivalRateMeterByType = Some(meterSensor(ActorMetricGroup, "By_Message_Type." + simpleName + "." + ArrivalRate))
        MessageProcessingTimerByType = Some(timerSensor(ActorMetricGroup, "By_Message_Type." + simpleName + "." + ProcessingTime))
      }

      ArrivalRateMeter.update(1)
      ArrivalRateMeterByType.foreach(_.update(1))
      beforeMessage()
      if (c.isDefinedAt(x)) try {
        c(x)
      } catch {
        case x: Throwable =>
          Error >>('Context -> "Error during message processing", 'Message -> x.getMessage)
          FailureRateMeter.update(1)

      }
      afterMessage()
      val ns: Long = System.nanoTime() - startStamp
      MessageProcessingTimer.updateNs(ns)
      MessageProcessingTimerByType.foreach(_.updateNs(ns))
  }

  final override def receive: Receive = wrapped(commonBehavior)

}

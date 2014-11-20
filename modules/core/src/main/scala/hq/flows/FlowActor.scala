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

package hq.flows

import akka.actor._
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorSubscriber, ActorPublisher}
import akka.stream.scaladsl._
import common.ToolExt.configHelper
import common.actors.{ActorWithConfigStore, PipelineWithStatesActor, SingleComponentActor}
import common._
import common.storage.{RemoveConfigFor, EntryConfigSnapshot, StoreSnapshot, ConfigStorageActor}
import hq._
import hq.flows.core.{FlowComponents, Builder}
import play.api.data
import play.api.libs.json.{JsValue, Json}

import scalaz.{Scalaz, -\/, \/-}
import Scalaz._

object FlowActor {
  def props(id: String) = Props(new FlowActor(id))

  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), id)
}


class FlowActor(id: String)
  extends PipelineWithStatesActor
  with ActorWithConfigStore
  with SingleComponentActor {

  implicit val mat = FlowMaterializer()
  implicit val dispatcher = context.system.dispatcher

  val configStore = ConfigStorageActor.path

  private var tapActor: Option[ActorRef] = None
  private var sinkActor: Option[ActorRef] = None
  private var flowActors: Option[Seq[ActorRef]] = None
  private var flow: Option[MaterializedMap] = None

  private var config = initialConfig

  override def key = ComponentKey("flow/" + id)

  override def preStart(): Unit = {

    logger.info(s"Starting flow actor with $config")

    //    if (isPipelineActive)
    //      switchToCustomBehavior(flowMessagesHandlerForOpenGate)
    //    else
    //      switchToCustomBehavior(flowMessagesHandlerForClosedGate)


    applyConfig(config)


    context.parent ! FlowAvailable(key)

    super.preStart()

  }

  override def commonBehavior: Actor.Receive = super.commonBehavior

  override def becomeActive(): Unit = {
    openTap()
    topicUpdate(T_INFO, info)
    //    switchToCustomBehavior(flowMessagesHandlerForOpenGate)
  }

  override def becomePassive(): Unit = {
    closeTap()
    topicUpdate(T_INFO, info)
    //    switchToCustomBehavior(flowMessagesHandlerForClosedGate)
  }

  def info = Some(Json.obj(
    "name" -> id,
    "text" -> (s"$id is " + (if (flow.isDefined) "valid" else "invalid")),
    "config" -> config,
    "state" -> (if (isPipelineActive) "active" else "passive")
  ))

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_INFO => topicUpdate(T_INFO, info, singleTarget = Some(ref))
  }

  override def processTopicCommand(ref: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_STOP =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Stopping the flow")
          self ! BecomePassive()
        case _ =>
          logger.info("Already stopped")
      }
    case T_START =>
      lastRequestedState match {
        case Some(Active()) =>
          logger.info("Already started")
        case _ =>
          logger.info("Starting the flow " + self.toString())
          self ! BecomeActive()
      }
    case T_KILL =>
      terminateFlow(Some("Flow being deleted"))
      configStore ! RemoveConfigFor("flow/"+id)
      self ! PoisonPill
    case T_EDIT =>
      for (
        data <- maybeData \/> Fail("No data");
        config <- data #>  'config \/> Fail("No configuration")
      ) applyConfig(config)
  }

  def closeTap() = {
    logger.debug(s"Tap closed")
    tapActor.foreach(_ ! BecomePassive())
    flowActors.foreach(_.foreach(_ ! BecomePassive()))
  }

  private def terminateFlow(reason: Option[String]) = {
    closeTap()
    tapActor.foreach(_ ! Stop(reason))
    tapActor = None
    sinkActor = None
    flowActors = None
    flow = None
  }

  private def openTap() = {
    logger.debug(s"Tap opened")
    flowActors.foreach(_.foreach(_ ! BecomeActive()))
    sinkActor.foreach(_ ! BecomeActive())
    tapActor.foreach(_ ! BecomeActive())
  }

  private def propsToActors(list: Seq[Props]) = list map context.actorOf

  private def resetFlowWith(tapProps: Props, pipeline : Seq[Props], sinkProps: Props) = {
    logger.debug(s"Resetting flow [$id]: tapProps: $tapProps, pipeline: $pipeline, sinkProps: $sinkProps")

    val tapA = context.actorOf(tapProps)
    val sinkA = context.actorOf(sinkProps)

    val pubSrc = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](tapA))
    val subSink = SubscriberSink(ActorSubscriber[JsonFrame](sinkA))

    val pipelineActors = propsToActors(pipeline)




    val flowPipeline = pipelineActors.foldRight[Sink[JsonFrame]](subSink) { (actor, sink) =>
      val s = SubscriberSink(ActorSubscriber[JsonFrame](actor))
      val p = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](actor))
      p.to(sink).run()
      s
    }

    flow = Some(pubSrc.to(flowPipeline).run())

    tapActor = Some(tapA)
    sinkActor = Some(sinkA)
    flowActors = Some(pipelineActors)

    if (isPipelineActive) openTap()
  }

  private def applyConfig(value: JsValue) = {
    config = value


    configStore ! StoreSnapshot(EntryConfigSnapshot("flow/"+id, Json.stringify(config), None))

    terminateFlow(Some("Applying new configuration"))

    val props = config #> 'config | Json.obj()
    Builder()(props, context) match {
      case -\/(fail) =>
        logger.info(s"Unable to build flow $id: failed with $fail")
      case \/-(FlowComponents(tap, pipeline, sink)) =>
        resetFlowWith(tap, pipeline, sink)
    }
    topicUpdate(T_INFO, info)
  }


  //  private def messageHandler: Receive = {
  //  }

}


package eventstreams.flows

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, PoisonPill, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl.{MaterializedMap, PublisherSource, Sink, SubscriberSink}
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors.{ActorWithActivePassiveBehaviors, BaseActorSysevents, StateChangeSysevents}
import eventstreams.flows.internal.{Builder, FlowComponents}
import play.api.libs.json._

import scalaz.{-\/, \/-}


trait FlowDeployableSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with StateChangeSysevents
  with WithSyseventPublisher {

  val FlowStarted = 'FlowStarted.trace
  val FlowConfigured = 'FlowConfigured.trace
  val ForwardedToFlow = 'ForwardedToFlow.trace

  override def componentId: String = "Flow.FlowInstance"
}
object FlowDeployableSysevents extends FlowDeployableSysevents

case class InitialiseDeployable(id: String, key: String, props: String, instructions: List[Config]) extends CommMessageJavaSer


class FlowDeployableActor
  extends ActorWithActivePassiveBehaviors
  with FlowDeployableSysevents
  with WithSyseventPublisher {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  implicit val mat = FlowMaterializer()
  implicit val dispatcher = context.system.dispatcher

  private var tapActor: Option[ActorRef] = None
  private var sinkActor: Option[ActorRef] = None
  private var flowActors: Option[Seq[ActorRef]] = None
  private var flow: Option[MaterializedMap] = None

  private var instanceId: String = "N/A"

  override def onBecameActive(): Unit = {
    openFlow()
  }

  override def onBecamePassive(): Unit = {
    closeFlow()
  }
  def closeFlow() = {
    tapActor.foreach(_ ! BecomePassive())
    flowActors.foreach(_.foreach(_ ! BecomePassive()))
  }

  private def init(id: String, key: String, props: String, instructions: List[Config]): Unit = {

    instanceId = id
    
    val config = Json.parse(props)
    Builder(instructions, config, context, key) match {
      case -\/(fail) =>
        println(s"!>>>>>> unable to build flow $fail")
        Warning >> ('Message -> "Unable to build the flow", 'Failure -> fail)
        self ! PoisonPill
      case \/-(FlowComponents(tap, pipeline, sink)) =>
        resetFlowWith(tap, pipeline, sink)
    }
  }

  private def openFlow() = {
    flowActors.foreach(_.foreach(_ ! BecomeActive()))
    sinkActor.foreach(_ ! BecomeActive())
    tapActor.foreach(_ ! BecomeActive())
    FlowStarted >>()
  }

  private def propsToActors(list: Seq[Props]) = list map context.actorOf

  private def resetFlowWith(tapProps: Props, pipeline: Seq[Props], sinkProps: Props) = {

    val tapA = context.actorOf(tapProps)
    val sinkA = context.actorOf(sinkProps)

    val pubSrc = PublisherSource[EventFrame](ActorPublisher[EventFrame](tapA))
    val subSink = SubscriberSink(ActorSubscriber[EventFrame](sinkA))

    val pipelineActors = propsToActors(pipeline)

    val flowPipeline = pipelineActors.foldRight[Sink[EventFrame]](subSink) { (actor, sink) =>
      val s = SubscriberSink(ActorSubscriber[EventFrame](actor))
      val p = PublisherSource[EventFrame](ActorPublisher[EventFrame](actor))
      p.to(sink).run()
      s
    }

    flow = Some(pubSrc.to(flowPipeline).run())

    tapActor = Some(tapA)
    sinkActor = Some(sinkA)
    flowActors = Some(pipelineActors)

    if (isComponentActive) openFlow()
  }


  private def handler: Receive = {
    case InitialiseDeployable(id, key, props, instructions) => init(id, key, props, instructions)
    case x: Acknowledgeable[_] =>
      println(s"!>>>>>received  $x -> $tapActor")
      ForwardedToFlow >> ('Messageid -> x.id, 'InstanceId -> instanceId)
      tapActor.foreach(_.forward(x))
  }

}

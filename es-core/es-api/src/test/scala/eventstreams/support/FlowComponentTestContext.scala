package eventstreams.support

import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor._
import akka.stream.scaladsl.{PublisherSource, SubscriberSink}
import akka.testkit.{TestKit, TestProbe}
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors._
import eventstreams.{BecomeActive, BecomePassive, EventFrame, Stop}

import scala.util.Try

trait FlowComponentTestContext {
  self: TestKit =>

  type TestFlowFunc = (TestFlowCtx) => Unit

  case class TestFlowCtx(pub: ActorRef, comp: ActorRef, sink: ActorRef)

  def withCustomFlow(pub: Props, component: Props, sink: Props)(f: TestFlowFunc) = {

    implicit val mat = FlowMaterializer()
    implicit val dispatcher = system.dispatcher

    val tapActorProbe = TestProbe()
    val sinkActorProbe = TestProbe()
    val componentActorProbe = TestProbe()


    val tapActor = system.actorOf(pub)
    val sinkActor = system.actorOf(sink)
    val componentActor = system.actorOf(component)

    val pubSrc = PublisherSource[EventFrame](ActorPublisher[EventFrame](tapActor))
    val subSink = SubscriberSink(ActorSubscriber[EventFrame](sinkActor))

    val componentAsSink = SubscriberSink(ActorSubscriber[EventFrame](componentActor))
    val componentAsPub = PublisherSource[EventFrame](ActorPublisher[EventFrame](componentActor))

    componentAsPub.to(subSink).run()
    pubSrc.to(componentAsSink).run()

    val ctx = TestFlowCtx(tapActor, componentActor, sinkActor)


    tapActorProbe watch tapActor
    sinkActorProbe watch sinkActor
    componentActorProbe watch componentActor

    try {
      f(ctx)
    } finally {

      tapActor ! BecomePassive
      componentActor ! BecomePassive
      sinkActor ! BecomePassive

      tapActor ! Stop(None)

      system.stop(tapActor)
      system.stop(componentActor)
      system.stop(sinkActor)
      Try {
        tapActorProbe expectTerminated tapActor
        sinkActorProbe expectTerminated sinkActor
        componentActorProbe expectTerminated componentActor
      }

    }


  }

  def withFlow(component: Props)(f: TestFlowFunc) = withCustomFlow(JsonFramePublisherStubActor.props, component, SinkStubActor.props())(f)

  def activateComponent()(implicit ctx: TestFlowCtx) = ctx.comp ! BecomeActive()

  def activateSink()(implicit ctx: TestFlowCtx) = ctx.sink ! BecomeActive()

  def activateFlow()(implicit ctx: TestFlowCtx): Unit = {
    activateComponent()
    activateSink()
  }

  def deactivateComponent()(implicit ctx: TestFlowCtx) = ctx.comp ! BecomePassive()

  def deactivateSink()(implicit ctx: TestFlowCtx) = ctx.sink ! BecomePassive()

  def deactivateFlow()(implicit ctx: TestFlowCtx): Unit = {
    deactivateComponent()
    deactivateSink()
  }

  def publishMsg(j: EventFrame)(implicit ctx: TestFlowCtx): Unit = ctx.pub ! j

}


trait JsonFramePublisherStubActorSysevents extends ComponentWithBaseSysevents with StateChangeSysevents with BaseActorSysevents {

  val PublishingMessage = 'PublishingMessage.trace
  val NoDemandAtPublisher = 'NoDemandAtPublisher.trace
  val NewDemandAtPublisher = 'NewDemandAtPublisher.trace

  override def componentId: String = "Test.JsonFramePublisherStubActor"
}

object JsonFramePublisherStubActor extends JsonFramePublisherStubActorSysevents {
  def props = Props(new JsonFramePublisherStubActor())
}

class JsonFramePublisherStubActor
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[EventFrame]
  with ActorWithActivePassiveBehaviors
  with JsonFramePublisherStubActorSysevents
  with WithSyseventPublisher {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def process(m: EventFrame) =
    if (totalDemand > 0) {
      PublishingMessage >> ('EventId -> m.eventIdOrNA)
      onNext(m)
    } else {
      NoDemandAtPublisher >>()
    }

  def handler: Receive = {
    case m: EventFrame => process(m)
//    case m: JsValue => process(EventFrame(m, Map()))
    case Request(n) => NewDemandAtPublisher >> ('Requested -> n)
  }

}

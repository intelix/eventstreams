package eventstreams.support

import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor._
import akka.stream.scaladsl.{PublisherSource, SubscriberSink}
import akka.testkit.{TestProbe, TestKit}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithComposableBehavior, PipelineWithStatesActor, StoppablePublisherActor}
import eventstreams.core.{BecomeActive, BecomePassive, JsonFrame, Stop}
import play.api.libs.json.JsValue

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

    val pubSrc = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](tapActor))
    val subSink = SubscriberSink(ActorSubscriber[JsonFrame](sinkActor))

    val componentAsSink = SubscriberSink(ActorSubscriber[JsonFrame](componentActor))
    val componentAsPub = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](componentActor))

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

  def withFlow(component: Props)(f: TestFlowFunc) = withCustomFlow(PublisherStubActor.props, component, SinkStubActor.props)(f)

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

  def publishMsg(j: JsValue)(implicit ctx: TestFlowCtx): Unit = ctx.pub ! j

}


trait PublisherStubActorEvents extends ComponentWithBaseEvents {

  val PublishingMessage = 'PublishingMessage.trace
  val NoDemandAtPublisher = 'NoDemandAtPublisher.trace
  val NewDemandAtPublisher = 'NewDemandAtPublisher.trace

  override def componentId: String = "Test.PublisherStubActor"
}

object PublisherStubActor extends PublisherStubActorEvents {
  def props = Props(new PublisherStubActor())
}

class PublisherStubActor
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[JsonFrame]
  with PipelineWithStatesActor
  with PublisherStubActorEvents
  with WithEventPublisher {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def process(m: JsonFrame) =
    if (totalDemand > 0) {
      PublishingMessage >> ('EventId --> m.eventIdOrNA)
      onNext(m)
    } else {
      NoDemandAtPublisher >>()
    }

  def handler: Receive = {
    case m: JsonFrame => process(m)
    case m: JsValue => process(JsonFrame(m, Map()))
    case Request(n) => NewDemandAtPublisher >> ('Requested --> n)
  }

}

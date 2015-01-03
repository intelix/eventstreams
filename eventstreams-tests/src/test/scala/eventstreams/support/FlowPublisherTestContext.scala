package eventstreams.support

import akka.actor.{ActorRef, Props}
import akka.stream.FlowMaterializer
import akka.stream.actor._
import akka.stream.scaladsl.{PublisherSource, SubscriberSink}
import akka.testkit.{TestKit, TestProbe}
import eventstreams.core.{BecomeActive, BecomePassive, JsonFrame, Stop}
import play.api.libs.json.JsValue

import scala.util.Try

trait FlowPublisherTestContext {
  self: TestKit =>

  type TestFlowFunc = (TestFlowCtx) => Unit

  case class TestFlowCtx(pub: ActorRef, sink: ActorRef)

  def withCustomFlow(pub: Props, sink: Props)(f: TestFlowFunc) = {

    implicit val mat = FlowMaterializer()
    implicit val dispatcher = system.dispatcher

    val tapActorProbe = TestProbe()
    val sinkActorProbe = TestProbe()


    val tapActor = system.actorOf(pub)
    val sinkActor = system.actorOf(sink)

    val pubSrc = PublisherSource[JsonFrame](ActorPublisher[JsonFrame](tapActor))
    val subSink = SubscriberSink(ActorSubscriber[JsonFrame](sinkActor))

    pubSrc.to(subSink).run()

    val ctx = TestFlowCtx(tapActor, sinkActor)


    tapActorProbe watch tapActor
    sinkActorProbe watch sinkActor
    
    try {
      f(ctx)
    } finally {

      tapActor ! BecomePassive
      sinkActor ! BecomePassive

      tapActor ! Stop(None)

      system.stop(tapActor)
      system.stop(sinkActor)
      Try {
        tapActorProbe expectTerminated tapActor
        sinkActorProbe expectTerminated sinkActor
      }

    }


  }

  def withFlow(pub: Props, sinkRequestStrategy: RequestStrategy)(f: TestFlowFunc) = withCustomFlow(pub, SinkStubActor.props(sinkRequestStrategy))(f)

  def activatePublisher(ctx: TestFlowCtx) = ctx.pub ! BecomeActive()
  def activateSink(ctx: TestFlowCtx) = ctx.sink ! BecomeActive()

  def activateFlow(ctx: TestFlowCtx): Unit = {
    activatePublisher(ctx)
    activateSink(ctx)
  }

  def setSinkRequestStrategyManual(ctx: TestFlowCtx): Unit = {
    ctx.sink ! NewRequestStrategy(ZeroRequestStrategy)
  }

  def sinkProduceDemand(count: Int, ctx: TestFlowCtx): Unit = {
    ctx.sink ! ProduceDemand(count)
  }

  def deactivatePublisher(ctx: TestFlowCtx) = ctx.pub ! BecomePassive()
  def deactivateSink(ctx: TestFlowCtx) = ctx.sink ! BecomePassive()

  def deactivateFlow(ctx: TestFlowCtx): Unit = {
    deactivatePublisher(ctx)
    deactivateSink(ctx)
  }

  def publishMsg(j: JsValue)(implicit ctx: TestFlowCtx): Unit = ctx.pub ! j

}





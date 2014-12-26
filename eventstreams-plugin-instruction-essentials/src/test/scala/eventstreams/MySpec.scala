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

package eventstreams

import _root_.core.events.support.EventAssertions
import akka.actor._
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor._
import akka.stream.scaladsl.{PublisherSource, SubscriberSink}
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import eventstreams.core.Types._
import eventstreams.core._
import eventstreams.core.actors._
import eventstreams.core.agent.core._
import eventstreams.plugins.essentials.GateInstruction
import eventstreams.plugins.essentials.GateInstructionConstants._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.libs.json.{JsValue, Json}

import scala.util.Try
import scalaz.{-\/, \/-}


object SinkStubActor {
  def props = Props(new SinkStubActor())
}

class SinkStubActor
  extends ActorWithComposableBehavior
  with ShutdownableSubscriberActor with PipelineWithStatesActor {


  var disableFlow = ZeroRequestStrategy
  var enableFlow = WatermarkRequestStrategy(1024, 96)

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case OnNext(msg) =>
      logger.warn("!>>>> at sink " + msg)
  }

  override protected def requestStrategy: RequestStrategy = lastRequestedState match {
    case Some(Active()) => enableFlow
    case _ => disableFlow
  }

}


private object PubllisherStubActor {
  def props = Props(new PubllisherStubActor())
}

private class PubllisherStubActor
  extends ActorWithComposableBehavior
  with ShutdownablePublisherActor[JsonFrame]
  with PipelineWithStatesActor {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def process(m: JsonFrame) =
    if (totalDemand > 0) {
      logger.warn("!>>> onNext for " + m)
      onNext(m)
    } else {
      logger.warn("!>>> no demand")
    }


  def handler: Receive = {
    case m: JsonFrame => process(m)
    case m: JsValue => process(JsonFrame(m, Map()))
    case Request(n) =>
      logger.warn(s"Downstream requested $n messages")
  }

}


private object GateStubActor {
  def props = Props(new GateStubActor())
}

private class GateStubActor
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor {


  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def handler: Receive = {
    case GateStateCheck(ref) => ref ! GateStateUpdate(GateOpen())
    case Acknowledgeable(msg, id) =>
      sender ! AcknowledgeAsReceived(id)
      sender ! AcknowledgeAsProcessed(id)
    case x => logger.warn("!>>>> at gate " + x)
  }

}


class MySpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll with EventAssertions {

  def this() = this(ActorSystem("MySpec"))


  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An Echo actor" must {

    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }

  }


  trait WithInstructionBuilder {
    def builder: BuilderFromConfig[InstructionType]

    def config: JsValue

    def state: Option[JsValue] = None

    def id: Option[String] = None

    var instruction: Option[InstructionType] = None

    def shouldNotBuild(f: Fail => Unit = _ => ()) = builder.build(config, state, id) match {
      case \/-(inst) => fail("Successfuly built, but expected to fail: " + inst)
      case -\/(x) => f(x)
    }

    def shouldBuild(f: InstructionType => Unit = _ => ()) = instruction match {
      case Some(inst) => f(inst)
      case None => builder.build(config, state, id) match {
        case \/-(inst) =>
          instruction = Some(inst)
          f(inst)
        case -\/(x) => fail("Failed with: " + x)
      }
    }
  }

  type TestFlowFunc = (TestFlowCtx) => Unit

  case class TestFlowCtx(pub: ActorRef, comp: ActorRef, sink: ActorRef)

  def withCustomFlow(pub: Props, component: Props, sink: Props)(f: TestFlowFunc) = {

    implicit val mat = FlowMaterializer()
    implicit val dispatcher = system.dispatcher

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


    val probe = TestProbe()

    probe watch tapActor
    probe watch sinkActor
    probe watch componentActor

    Try {
      f(ctx)
    }

    tapActor ! BecomePassive
    componentActor ! BecomePassive
    sinkActor ! BecomePassive

    tapActor ! Stop(None)

    system.stop(tapActor)
    system.stop(componentActor)
    system.stop(sinkActor)

    probe expectTerminated tapActor
    probe expectTerminated sinkActor
    probe expectTerminated componentActor

  }

  def withFlow(component: Props)(f: TestFlowFunc) = withCustomFlow(PubllisherStubActor.props, component, SinkStubActor.props)(f)

  def withGateStub(f: ActorRef => Unit) = {
    val cfg = Json.obj(CfgFAddress -> "/user/testGate")
    val gateInstr = new GateInstruction().build(cfg, None, None)
    val mockGate = system.actorOf(GateStubActor.props, "testGate")
    val probe = TestProbe()
    probe watch mockGate
    Try {
      f(mockGate)
    }
    system.stop(mockGate)
    probe.expectTerminated(mockGate)
  }

  def activateFlow()(implicit ctx: TestFlowCtx): Unit = {
    ctx.pub ! BecomeActive()
    ctx.comp ! BecomeActive()
    ctx.sink ! BecomeActive()
  }

  def publishMsg(j: JsValue)(implicit ctx: TestFlowCtx): Unit = ctx.pub ! j

  import eventstreams.plugins.essentials.GateInstructionConstants._


  trait WithGateInstructionContext extends WithInstructionBuilder {
    def withGateInstructionFlow(f: TestFlowFunc) = {
      shouldBuild { instr =>
        withGateStub { mockGate =>
          withFlow(instr) { ctx => f(ctx)}
        }
      }
    }
  }

  trait WithBasicConfig extends WithGateInstructionContext {
    override def builder: BuilderFromConfig[InstructionType] = new GateInstruction()

    override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate")
  }


  "Test X" must {
    "try 1" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectAnyEvent(GateStateMonitorStarted)
        publishMsg(Json.obj("value" -> "1"))
        expectAnyEvent(1, MessagePublished)
      }
    }
    "try 2" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectAnyEvent(GateStateMonitorStarted)
        publishMsg(Json.obj("value" -> "1"))
        publishMsg(Json.obj("value" -> "2"))
        publishMsg(Json.obj("value" -> "3"))
        expectAnyEvent(3, MessagePublished)
      }
    }
    "try 3" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectAnyEvent(GateStateMonitorStarted)
        (1 to 20) foreach { _ =>
          publishMsg(Json.obj("value" -> "1"))
        }
        expectAnyEvent(20, MessagePublished)
      }
    }
    "try 4" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectAnyEvent(GateStateMonitorStarted)
        (1 to 100) foreach { _ =>
          publishMsg(Json.obj("value" -> "1"))
        }
        expectAnyEvent(100, MessagePublished)
      }
    }
    "try 5" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectAnyEvent(GateStateMonitorStarted)
        publishMsg(Json.obj("value" -> "1"))
        publishMsg(Json.obj("value" -> "2"))
        expectAnyEvent(2, MessagePublished)
      }
    }

  }


}
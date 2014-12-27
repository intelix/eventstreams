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

import _root_.core.events.EventOps.symbolToEventField
import akka.actor._
import eventstreams.core.Types._
import eventstreams.core._
import eventstreams.plugins.essentials.GateInstructionConstants._
import eventstreams.plugins.essentials.{GateInstruction, GateInstructionConstants}
import eventstreams.support._
import play.api.libs.json.{JsValue, Json}

class GateInstructionTest(_system: ActorSystem)
  extends ActorTestContext(_system)
  with FlowComponentTestContext
  with GateStub {


  def this() = this(ActorSystem("TestSystem"))


  trait WithGateInstructionContext extends InstructionBuilderTestContext {
    def withGateInstructionFlow(f: TestFlowFunc) = {
      shouldBuild { instr =>
        withFlow(instr) { ctx => f(ctx)}
      }
    }
  }

  trait WithBasicConfig extends WithGateInstructionContext {
    override def builder: BuilderFromConfig[InstructionType] = new GateInstruction()

    override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2)
  }


  "GateInstruction" must {

    "be built with valid config" in new WithBasicConfig {
      shouldBuild()
    }

    s"not be built if $CfgFAddress is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj()

      shouldNotBuild()
    }

    "have a new instance when added to the flow" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        expectSomeEvents(GateInstructionConstants.GateInstance)
      }
    }

    "initially be stopped" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        withGateStub { gate =>
          waitAndCheck { () =>
            expectNoEvents(GateStateMonitorStarted)
            expectNoEvents(GateInstructionConstants.Starting)
            expectNoEvents(GateInstructionConstants.ConnectedToGate)
          }
        }
      }
    }

    "propagate demand to the publisher" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
      }
    }

    "activate on request" in new WithBasicConfig {
      withGateInstructionFlow { implicit ctx =>
        activateFlow()
        expectSomeEvents(GateInstructionConstants.Starting)
      }
    }

    "when activated with gate available but closed" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            withGateStub { gate =>
              activateFlow()
              expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
              f(ctx)
            }
          }
        }
      }

      "connect to the gate" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.ConnectedToGate)
        }
      }
      "start gate state monitoring" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.GateStateMonitorStarted)
        }
      }
      "react to closed gate signal" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.MonitoredGateStateChanged, 'NewState --> "GateClosed()")
        }

      }
      "accept flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }

      }
      "schedule incoming message for delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveringToActor)
          }
        }
      }
      "not deliver to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveringToActor)
          }
        }
      }
      "not deliver to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "accept second flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
        }
      }
      "schedule second message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 2)
        }
      }
      "not deliver to the gate after second message scheduled" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveringToActor)
          }
        }
      }
      "not deliver second message to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "not see third message as publisher will not publish it without further demand" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(PublisherStubActor.NoDemandAtPublisher)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.MessageArrived)
          }
        }
      }
    }



    "when activated with gate available and open but not acking at gate" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            withGateStub { gate =>
              activateFlow()
              expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
              openGate(gate)
              f(ctx)
            }
          }
        }
      }

      "connect to the gate" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.ConnectedToGate)
        }
      }
      "start gate state monitoring" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.GateStateMonitorStarted)
        }
      }
      "react to open gate signal" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.MonitoredGateStateChanged, 'NewState --> "GateOpen()")
        }

      }
      "accept flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }

      }
      "schedule incoming message for delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
        }
      }
      "attempt re-delivery " in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
          expectSomeEvents(2,GateInstructionConstants.DeliveringToActor)
          expectSomeEvents(3,GateInstructionConstants.DeliveringToActor)
          expectSomeEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 0)
          expectSomeEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 1)
          expectSomeEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 2)
        }
      }
      "not deliver to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "deliver to the sink if blocking delivery option turned off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "accept second flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
        }
      }
      "schedule second message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 2)
        }
      }
      "deliver second message to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveringToActor)
        }
      }
      "not deliver second message to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "deliver second message to the sink if blocking delivery is off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "not see third message as publisher will not publish it without further demand" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(PublisherStubActor.NoDemandAtPublisher)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.MessageArrived)
          }
        }
      }
      "accept third message if in flights configured to more than two" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 3, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }
      } 
    }




    "when activated with gate available and open and gate acking as received but not acking as processed" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            withGateStub { gate =>
              activateFlow()
              expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
              openGate(gate)
              autoAckAsReceivedAtGate(gate)
              f(ctx)
            }
          }
        }
      }

      "connect to the gate" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.ConnectedToGate)
        }
      }
      "start gate state monitoring" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.GateStateMonitorStarted)
        }
      }
      "react to open gate signal" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.MonitoredGateStateChanged, 'NewState --> "GateOpen()")
        }

      }
      "accept flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }

      }
      "schedule incoming message for delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
        }
      }
      "not attempt re-delivery " in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 1)
          }
        }
      }
      "confirm delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveryConfirmed)
        }
      }
      "not confirm processing" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.ProcessingConfirmed)
          }
        }
      }
      "not deliver to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "deliver to the sink if blocking delivery option turned off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "accept second flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
        }
      }
      "schedule second message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 2)
        }
      }
      "deliver second message to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveringToActor)
        }
      }
      "confirm delivery of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveryConfirmed)
        }
      }
      "not confirm processing of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.ProcessingConfirmed)
          }
        }
      }
      "not deliver second message to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }
      "deliver second message to the sink if blocking delivery is off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "not see third message as publisher will not publish it without further demand" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(PublisherStubActor.NoDemandAtPublisher)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.MessageArrived)
          }
        }
      }
      "accept third message if in flights configured to more than two" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 3, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }
      }
    }


    "when activated with gate available and open and gate acking as received followed by processed" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            withGateStub { gate =>
              activateFlow()
              expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
              openGate(gate)
              autoAckAsReceivedAtGate(gate)
              autoAckAsProcessedAtGate(gate)
              f(ctx)
            }
          }
        }
      }

      "connect to the gate" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.ConnectedToGate)
        }
      }
      "start gate state monitoring" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.GateStateMonitorStarted)
        }
      }
      "react to open gate signal" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.MonitoredGateStateChanged, 'NewState --> "GateOpen()")
        }

      }
      "accept flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }

      }
      "schedule incoming message for delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
        }
      }
      "not attempt re-delivery " in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 1)
          }
        }
      }
      "confirm delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.DeliveryConfirmed)
        }
      }
      "confirm processing" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.ProcessingConfirmed)
        }
      }
      "confirm full ack" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.FullAcknowledgement)
        }
      }
      "confirm publishing" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.MessagePublished)
        }
      }
      "deliver to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "deliver to the sink once if blocking delivery option turned off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "accept second flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
        }
      }
      "schedule second message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver second message to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveringToActor)
        }
      }
      "confirm delivery of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveryConfirmed)
        }
      }
      "confirm processing of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.ProcessingConfirmed)
        }
      }
      "confirm full ack of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.FullAcknowledgement)
        }
      }
      "confirm publishing of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessagePublished)
        }
      }
      "deliver second message to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2")) 
          expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "deliver second message to the sink once if blocking delivery is off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
          }
          
        }
      }
      "see third message as there should be further demand at publisher" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          expectSomeEvents(2, PublisherStubActor.NewDemandAtPublisher)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }
      }
      "have empty queues when third message arrives" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessagePublished)
          expectSomeEvents(2, PublisherStubActor.NewDemandAtPublisher)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
        }
      }
    }



    "when activated with gate available and open and gate acking only as processed" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            withGateStub { gate =>
              activateFlow()
              expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
              openGate(gate)
              autoAckAsProcessedAtGate(gate)
              f(ctx)
            }
          }
        }
      }

      "connect to the gate" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.ConnectedToGate)
        }
      }
      "start gate state monitoring" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.GateStateMonitorStarted)
        }
      }
      "react to open gate signal" in new LocalCtx {
        run { implicit ctx =>
          expectSomeEvents(GateInstructionConstants.MonitoredGateStateChanged, 'NewState --> "GateOpen()")
        }

      }
      "accept flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }

      }
      "schedule incoming message for delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
        }
      }
      "not attempt re-delivery " in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.DeliveringToActor)
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveryAttempt, 'Attempt --> 1)
          }
        }
      }
      "not confirm delivery" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveryConfirmed)
          }
        }
      }
      "confirm processing" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.ProcessingConfirmed)
        }
      }
      "confirm full ack" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.FullAcknowledgement)
        }
      }
      "confirm publishing" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, GateInstructionConstants.MessagePublished)
        }
      }
      "deliver to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "deliver to the sink once if blocking delivery option turned off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(1, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "accept second flow message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
        }
      }
      "schedule second message" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
          clearEvents()
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery)
        }
      }
      "deliver second message to the gate" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.DeliveringToActor)
        }
      }
      "not confirm delivery of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectNoEvents(GateInstructionConstants.DeliveryConfirmed)
          }
        }
      }
      "confirm processing of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.ProcessingConfirmed)
        }
      }
      "confirm full ack of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.FullAcknowledgement)
        }
      }
      "confirm publishing of the second" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessagePublished)
        }
      }
      "deliver second message to the sink" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
        }
      }
      "deliver second message to the sink once if blocking delivery is off" in new LocalCtx {
        override def config: JsValue = Json.obj(CfgFAddress -> "/user/testGate", CfgFBuffer -> 2, CfgFBlockingDelivery -> false)
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          waitAndCheck { () =>
            expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
          }

        }
      }
      "see third message as there should be further demand at publisher" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessageArrived)
          expectSomeEvents(2, PublisherStubActor.NewDemandAtPublisher)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived)
        }
      }
      "have empty queues when third message arrives" in new LocalCtx {
        run { implicit ctx =>
          publishMsg(Json.obj("value" -> "1"))
          publishMsg(Json.obj("value" -> "2"))
          expectSomeEvents(2, GateInstructionConstants.MessagePublished)
          expectSomeEvents(2, PublisherStubActor.NewDemandAtPublisher)
          clearEvents()
          publishMsg(Json.obj("value" -> "3"))
          expectSomeEvents(GateInstructionConstants.MessageArrived, 'PublisherQueueDepth --> 0)
          expectSomeEvents(GateInstructionConstants.ScheduledForDelivery, 'DeliveryQueueDepth --> 1)
        }
      }
    }

    "when activated with gate unavailable and two messages pending" must {

      trait LocalCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withGateInstructionFlow { implicit ctx =>
            activateFlow()
            expectSomeEvents(PublisherStubActor.NewDemandAtPublisher)
            publishMsg(Json.obj("value" -> "1"))
            publishMsg(Json.obj("value" -> "2"))
            expectSomeEvents(2, GateInstructionConstants.ScheduledForDelivery)
            f(ctx)
          }
        }
      }

      "not deliver to sink" in new LocalCtx {
        run { implicit ctx =>
          waitAndCheck { () =>
            expectNoEvents(SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }

      "not attempt delivery to gate" in new LocalCtx {
        run { implicit ctx =>
          waitAndCheck { () =>
            expectNoEvents(DeliveringToActor)
          }
        }
      }

      "attempt delivery to gate when gate arrives" in new LocalCtx {
        run { implicit ctx =>
          withGateStub { gate =>
            openGate(gate)
            expectSomeEvents(2, DeliveringToActor)
          }
        }
      }

      "re-deliver until messages acked" in new LocalCtx {
        run { implicit ctx =>
          withGateStub { gate =>
            openGate(gate)
            expectSomeEvents(1, DeliveryAttempt, 'Attempt --> 1)
            autoAckAsProcessedAtGate(gate)
            expectSomeEvents(2, SinkStubActor.ReceivedMessageAtSink)
          }
        }
      }


    }


    }


}
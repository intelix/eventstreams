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

package eventstreams

import akka.actor._
import eventstreams.instructions.Types._
import eventstreams.support._
import eventstreams.tx.TransactionInstructionConstants._
import eventstreams.tx.{TransactionInstruction, TransactionInstructionConstants}
import play.api.libs.json.{JsValue, Json}

class TransactionInstructionTest(_system: ActorSystem)
  extends ActorTestContext(_system)
  with FlowComponentTestContext {


  def this() = this(ActorSystem("TestSystem"))


  trait WithTxInstructionContext extends BuilderFromConfigTestContext {
    def withTxInstructionFlow(f: TestFlowFunc) = {
      shouldBuild { instr =>
        withFlow(instr) { ctx => f(ctx)}
      }
    }
  }

  trait WithBasicConfig extends WithTxInstructionContext {
    override def builder: BuilderFromConfig[InstructionType] = new TransactionInstruction()

    override def config: JsValue = Json.obj(
      CfgFCorrelationIdTemplates -> "${f1},${f2}",
      CfgFMaxOpenTransactions -> 5,
      CfgFTargetTxField -> "tx",
      CfgFTags -> "t1,tag_${first.streamKey}",
      CfgFTxTypeTemplate -> "type_${first.streamKey}",
      CfgFBuffer -> 10,
      CfgFTxStartCondition -> "type=start",
      CfgFTxEndSuccessCondition -> "type=endsuccess",
      CfgFTxEndFailureCondition -> "type=endfailure",
      CfgFClass -> "tx"
    )
  }


  "TransactionInstruction" must {

    "be built with valid config" in new WithBasicConfig {
      shouldBuild()
    }

    s"not be built if $CfgFCorrelationIdTemplates is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj(
        CfgFMaxOpenTransactions -> 5,
        CfgFTargetTxField -> "tx",
        CfgFBuffer -> 2,
        CfgFTxStartCondition -> "type=start",
        CfgFTxEndSuccessCondition -> "type=endsuccess",
        CfgFTxEndFailureCondition -> "type=endfailure",
        CfgFClass -> "tx"
      )

      shouldNotBuild()
    }

    s"not be built if $CfgFTxStartCondition is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj(
        CfgFCorrelationIdTemplates -> "${f1},${f2}",
        CfgFMaxOpenTransactions -> 5,
        CfgFTargetTxField -> "tx",
        CfgFBuffer -> 2,
        CfgFTxEndSuccessCondition -> "type=endsuccess",
        CfgFTxEndFailureCondition -> "type=endfailure",
        CfgFClass -> "tx"
      )

      shouldNotBuild()
    }

    s"not be built if $CfgFTxEndSuccessCondition is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj(
        CfgFCorrelationIdTemplates -> "${f1},${f2}",
        CfgFMaxOpenTransactions -> 5,
        CfgFTargetTxField -> "tx",
        CfgFBuffer -> 2,
        CfgFTxStartCondition -> "type=start",
        CfgFTxEndFailureCondition -> "type=endfailure",
        CfgFClass -> "tx"
      )

      shouldNotBuild()
    }



    "have a new instance when added to the flow" in new WithBasicConfig {
      withTxInstructionFlow { implicit ctx =>
        expectOneOrMoreEvents(TransactionInstructionConstants.TxInstructionInstance)
      }
    }

    "initially be stopped" in new WithBasicConfig {
      withTxInstructionFlow { implicit ctx =>
        waitAndCheck {
          expectNoEvents(TransactionInstructionConstants.BecomingActive)
        }
      }
    }

    "propagate demand to the publisher" in new WithBasicConfig {
      withTxInstructionFlow { implicit ctx =>
        expectOneOrMoreEvents(JsonFramePublisherStubActor.NewDemandAtPublisher)
      }
    }

    "activate on request" in new WithBasicConfig {
      withTxInstructionFlow { implicit ctx =>
        activateFlow()
        expectOneOrMoreEvents(TransactionInstructionConstants.BecomingActive)
      }
    }

    "when activated" must {

      trait ActivatedCtx extends WithBasicConfig {
        def run(f: TestFlowFunc) = {
          withTxInstructionFlow { implicit ctx =>
            activateFlow()
            expectOneOrMoreEvents(JsonFramePublisherStubActor.NewDemandAtPublisher)
            f(ctx)
          }
        }
      }

      "start a transaction if start condition met" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectExactlyNEvents(1, TransactionStart, 'EventId -> "1")
        }
      }

      "start and complete a transaction if start condition met, followed by finish (success) condition" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
          expectExactlyNEvents(3, SinkStubActor.ReceivedMessageAtSink)
          expectExactlyNEvents(1, TransactionStart, 'EventId -> "1")
          expectExactlyNEvents(1, TransactionFinish, 'EventId -> "2")
          expectExactlyNEvents(1, TransactionCompleted, 'EventIdStart -> "1", 'EventIdFinish -> "2", 'TxId -> "tid1", 'Success -> true)
        }
      }

      trait WithOpenTxCtx extends ActivatedCtx {
        override def run(f: TestFlowFunc) = {
          super.run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, TransactionStart, 'EventId -> "1")
            clearEvents()
            f(ctx)
          }
        }
      }


      "ignore event if none of the transaction id fields are available" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("type" -> "start", "eventId" -> "1", "date_ts" -> 1000))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectNoEvents(TransactionStart)
        }
      }

      "ignore event if none of the transaction id fields have any value" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectNoEvents(TransactionStart)
        }
      }

      "ignore event if neither start nor stop conditions are met" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "abc", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectNoEvents(TransactionStart)
        }
      }

      "ignore event if eventId is not set" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "date_ts" -> 1000, "streamKey" -> "key"))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectNoEvents(TransactionStart)
        }
      }

      "ignore event if timestamp field not available" in new ActivatedCtx {
        run { implicit ctx =>
          publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "streamKey" -> "key"))
          expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
          expectNoEvents(TransactionStart)
        }
      }

      "... with one open (with start) tx" must {

        "complete tx if end (success) condition is met" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, TransactionFinish, 'EventId -> "2")
            expectExactlyNEvents(1, TransactionCompleted, 'EventIdStart -> "1", 'EventIdFinish -> "2", 'TxId -> "tid1", 'Success -> true)
          }
        }

        "complete tx if end (failure) condition is met" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, TransactionFinish, 'EventId -> "2")
            expectExactlyNEvents(1, TransactionCompleted, 'EventIdStart -> "1", 'EventIdFinish -> "2", 'TxId -> "tid1", 'Success -> false)
          }
        }

        "complete tx if end (success) condition is met, when tx id is in another field" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f2" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, TransactionFinish, 'EventId -> "2")
            expectExactlyNEvents(1, TransactionCompleted, 'EventIdStart -> "1", 'EventIdFinish -> "2", 'TxId -> "tid1", 'Success -> true)
          }
        }

        "complete tx if end (failure) condition is met, when tx id is in another field" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f2" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, TransactionFinish, 'EventId -> "2")
            expectExactlyNEvents(1, TransactionCompleted, 'EventIdStart -> "1", 'EventIdFinish -> "2", 'TxId -> "tid1", 'Success -> false)
          }
        }


        "ignore event if none of the transaction id fields are available" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("type" -> "start", "eventId" -> "1", "date_ts" -> 1000))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectNoEvents(TransactionStart)
          }
        }

        "ignore event if none of the transaction id fields have any value" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectNoEvents(TransactionStart)
          }
        }

        "ignore event if neither start nor stop conditions are met" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "abc", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectNoEvents(TransactionStart)
          }
        }

        "ignore event if eventId is not set" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectNoEvents(TransactionStart)
          }
        }

        "ignore event if timestamp field not available" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            expectNoEvents(TransactionStart)
          }
        }

        "not complete tx if end condition is not met" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "start", "eventId" -> "1", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink)
            waitAndCheck {
              expectNoEvents(TransactionCompleted)
            }
          }
        }

        "once completed (success), produce eventId as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2")
          }
        }

        "once completed (success), produce streamKey as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'StreamKey -> "Some(key)")
          }
        }

        "once completed (success), produce streamSeed as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'StreamSeed -> "Some(0)")
          }
        }

        "once completed (success), produce txType as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"txType":"type_key"""".r)
          }
        }

        "once completed (success), produce tags as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"tags":\["t1","tag_key"\]""".r)
          }
        }

        "once completed (success), include first event of the tx" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"first":\{""".r)
          }
        }

        "once completed (success), include last event of the tx" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"last":\{""".r)
          }
        }

        "once completed (success), include tx id" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"tx":"tid1"""".r)
          }
        }

        "once completed (success), include tx elapsed time" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2500, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"txElapsedMs":1500""".r)
          }
        }

        "once completed (success), include tx status" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endsuccess", "eventId" -> "2", "date_ts" -> 2500, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> s""""txStatus":"$ValueStatusClosedSuccess"""".r)
          }
        }




        "once completed (failure), produce eventId as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2")
          }
        }

        "once completed (failure), produce streamKey as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'StreamKey -> "Some(key)")
          }
        }

        "once completed (failure), produce streamSeed as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'StreamSeed -> "Some(0)")
          }
        }

        "once completed (failure), produce txType as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"txType":"type_key"""".r)
          }
        }

        "once completed (failure), produce tags as per config" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"tags":\["t1","tag_key"\]""".r)
          }
        }

        "once completed (failure), include first event of the tx" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"first":\{""".r)
          }
        }

        "once completed (failure), include last event of the tx" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"last":\{""".r)
          }
        }

        "once completed (failure), include tx id" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"tx":"tid1"""".r)
          }
        }

        "once completed (failure), include tx elapsed time" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2500, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> """"txElapsedMs":1500""".r)
          }
        }

        "once completed (failure), include tx status" in new WithOpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "tid1", "type" -> "endfailure", "eventId" -> "2", "date_ts" -> 2500, "streamKey" -> "key"))
            expectExactlyNEvents(2, SinkStubActor.ReceivedMessageAtSink)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "1:2", 'JsonContents -> s""""txStatus":"$ValueStatusClosedFailure"""".r)
          }
        }




      }

      "... with four open txs (t2:[X{1000},_], t1:[X{2000},_], t1:[_,S{1500}], t1:[_,F{500}])" must {

        trait With4OpenTxCtx extends ActivatedCtx {
          override def run(f: TestFlowFunc) = {
            super.run { implicit ctx =>
              publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2000", "date_ts" -> 2000, "streamKey" -> "key"))
              publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S1500", "date_ts" -> 1500, "streamKey" -> "key"))
              publishMsg(EventFrame("f1" -> "t1", "type" -> "endfailure", "eventId" -> "t1F500", "date_ts" -> 500, "streamKey" -> "key"))
              publishMsg(EventFrame("f1" -> "t2", "type" -> "start", "eventId" -> "t2X1000", "date_ts" -> 1000, "streamKey" -> "key"))
              expectExactlyNEvents(4, SinkStubActor.ReceivedMessageAtSink)
              expectExactlyNEvents(2, TransactionStart)
              expectExactlyNEvents(2, TransactionFinish)
              expectNoEvents(TransactionCompleted)
              clearEvents()
              f(ctx)
            }
          }
        }

        "complete t2X1000 with S@1000" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t2X1000 with F@1000" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endfailure", "eventId" -> "X", "date_ts" -> 1000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t2X1000 with S@1001" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 1001, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t2X1000 with F@1001" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endfailure", "eventId" -> "X", "date_ts" -> 1001, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t2X1000 with S@2000" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":1000""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "not complete t2X1000 with S@999" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 999, "streamKey" -> "key"))
            waitAndCheck {
              expectNoEvents(TransactionCompleted, 'EventId -> "t2X1000:X", 'JsonContents -> s""""txElapsedMs":1000""".r)
            }
          }
        }

        "not complete t2X1000 with S@999 - new transaction must be added, closeable with X@100" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 999, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t2", "type" -> "start", "eventId" -> "X2", "date_ts" -> 100, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X2:X", 'JsonContents -> s""""txElapsedMs":899""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 4)
          }
        }

        "not complete t2X1000 with S@999, complete t2X1000 with S@1000 and complete S@999 with X@100" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 999, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "X2", "date_ts" -> 1000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t2", "type" -> "start", "eventId" -> "X3", "date_ts" -> 100, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X3:X", 'JsonContents -> s""""txElapsedMs":899""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 4)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:X2", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1X2000 with S@2000" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:X", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1X2000 with S@2001" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "X", "date_ts" -> 2001, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:X", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1X2000 with F@2000" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endfailure", "eventId" -> "X", "date_ts" -> 2000, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:X", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1X2000 with F@2001" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endfailure", "eventId" -> "X", "date_ts" -> 2001, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:X", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "not complete t1X2000 with S@1999" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S1999", "date_ts" -> 1999, "streamKey" -> "key"))
            waitAndCheck {
              expectNoEvents(TransactionCompleted)
            }
          }
        }

        "not complete t1X2000 with S@1999, complete t1X2000 with S@2000 and complete t1S1999 with X@1998" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S1999", "date_ts" -> 1999, "streamKey" -> "key"))
            waitAndCheck {
              expectNoEvents(TransactionCompleted)
            }
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endfailure", "eventId" -> "X", "date_ts" -> 2000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X2", "date_ts" -> 1998, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:X", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X2:t1S1999", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1S1500 with X@1500" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 1500, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1S1500", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1S1500 with X@1499" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 1499, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1S1500", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "not complete t1S1500 with X@1501" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 1501, "streamKey" -> "key"))
            expectExactlyNEvents(1, NewTransaction, 'OpenTxCount -> 5)
            waitAndCheck {
              expectNoEvents(TransactionCompleted)
            }
          }
        }

        "complete t1F500 with X@500" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 500, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1F500", 'JsonContents -> s""""txElapsedMs":0""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1F500 with X@499" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 499, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1F500", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
          }
        }

        "complete t1F500 with X@499 and complete t1S1500 with another X@499" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 499, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X2", "date_ts" -> 499, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1F500", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X2:t1S1500", 'JsonContents -> s""""txElapsedMs":1001""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 2)
          }
        }

        "complete t1F500 with X@499 and complete t1S1500 with another X@499, and next X@499 will open a new tx completable with S@499" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X", "date_ts" -> 499, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X2", "date_ts" -> 499, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "X3", "date_ts" -> 499, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "S4", "date_ts" -> 499, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X:t1F500", 'JsonContents -> s""""txElapsedMs":1""".r)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 3)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X2:t1S1500", 'JsonContents -> s""""txElapsedMs":1001""".r)
            expectExactlyNEvents(2, TransactionCompleted, 'OpenTxCount -> 2)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "X3:S4", 'JsonContents -> s""""txElapsedMs":0""".r)
          }
        }


        "complete all in random order (oldest to recent), with 5th tx added t1X2500" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))

            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X500", "date_ts" -> 500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X1500", "date_ts" -> 1500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2000", "date_ts" -> 2000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "t2S1000", "date_ts" -> 1000, "streamKey" -> "key"))

            expectExactlyNEvents(5, TransactionCompleted)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 0)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X500:t1F500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X1500:t1S1500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:t1S2000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2500:t1S2500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:t2S1000")

          }
        }


        "complete all in random order (recent to oldest), with 5th tx added t1X2500" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))

            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "t2S1000", "date_ts" -> 1000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2000", "date_ts" -> 2000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X1500", "date_ts" -> 1500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X500", "date_ts" -> 500, "streamKey" -> "key"))

            expectExactlyNEvents(5, TransactionCompleted)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 0)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X500:t1F500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X1500:t1S1500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:t1S2000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2500:t1S2500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:t2S1000")

          }
        }

        "complete all in random order (mix), with 5th tx added t1X2500" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))

            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X500", "date_ts" -> 500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "endsuccess", "eventId" -> "t1S2000", "date_ts" -> 2000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t2", "type" -> "endsuccess", "eventId" -> "t2S1000", "date_ts" -> 1000, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X1500", "date_ts" -> 1500, "streamKey" -> "key"))

            expectExactlyNEvents(5, TransactionCompleted)
            expectExactlyNEvents(1, TransactionCompleted, 'OpenTxCount -> 0)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X500:t1F500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X1500:t1S1500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:t1S2000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2500:t1S2500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:t2S1000")

          }
        }

        "evict t1F500 when [t1X2500, t1X3500] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))

            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }

        "evict t1F500,t2X1000 when [t1X2500, t1X3500, t1X4500] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X4500", "date_ts" -> 4500, "streamKey" -> "key"))

            expectExactlyNEvents(2, TransactionEvicted)
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t2X1000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }

        "evict t1F500,t1S1500,t2X1000 when [t1X2500, t1X3500, t1X4500, t1X5500] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X4500", "date_ts" -> 4500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X5500", "date_ts" -> 5500, "streamKey" -> "key"))

            expectExactlyNEvents(3, TransactionEvicted)
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1S1500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t2X1000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1S1500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }

        "evict t1F500,t1S1500,t2X1000,t1X2000 when [t1X2500, t1X3500, t1X4500, t1X5500, t1X6500] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X4500", "date_ts" -> 4500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X5500", "date_ts" -> 5500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X6500", "date_ts" -> 6500, "streamKey" -> "key"))

            expectExactlyNEvents(4, TransactionEvicted)
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1S1500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t2X1000")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2000")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1S1500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }

        "evict t1F500,t1S1500,t2X1000,t1X2000,t1X2500 when [t1X2500, t1X3500, t1X4500, t1X5500, t1X6500, t1X7500] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X4500", "date_ts" -> 4500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X5500", "date_ts" -> 5500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X6500", "date_ts" -> 6500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X6500", "date_ts" -> 7500, "streamKey" -> "key"))

            expectExactlyNEvents(5, TransactionEvicted)
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1S1500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t2X1000")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2000")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2500")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1S1500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2500:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }

        "evict t1F500,t1S1500,t2X1000,t1X2000,t1X2500,t1X2200 when [t1X2500, t1X3500, t1X4500, t1X5500, t1X6500, t1X7500, t1X2200] added (open tx limit exceeded)" in new With4OpenTxCtx {
          run { implicit ctx =>
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2500", "date_ts" -> 2500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X3500", "date_ts" -> 3500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X4500", "date_ts" -> 4500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X5500", "date_ts" -> 5500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X6500", "date_ts" -> 6500, "streamKey" -> "key"))
            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X6500", "date_ts" -> 7500, "streamKey" -> "key"))
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1F500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> ":t1S1500", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t2X1000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2000:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2500:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

            publishMsg(EventFrame("f1" -> "t1", "type" -> "start", "eventId" -> "t1X2200", "date_ts" -> 2200, "streamKey" -> "key"))

            expectExactlyNEvents(6, TransactionEvicted)
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1F500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdFinish -> "t1S1500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t2X1000")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2000")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2500")
            expectExactlyNEvents(1, TransactionEvicted, 'EventIdStart -> "t1X2200")
            expectExactlyNEvents(1, SinkStubActor.ReceivedMessageAtSink, 'EventId -> "t1X2200:", 'JsonContents -> s""""txStatus":"$ValueStatusIncomplete"""".r)

          }
        }



      }


    }


  }

}
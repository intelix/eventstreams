package eventstreams

import _root_.core.events.EventOps.symbolToEventField
import akka.actor.ActorSystem
import eventstreams.core.agent.core.ProducedMessage
import eventstreams.ds.plugins.filetailer.FileTailerConstants._
import eventstreams.support.ActorTestContext
import eventstreams.support.SinkStubActor._
import play.api.libs.json._
import play.api.libs.json.extensions._

class FileTailerDatasourceCursorTest(_system: ActorSystem)
  extends ActorTestContext(_system)
  with FileTailerTestSupport {

  def this() = this(ActorSystem("TestSystem"))


  "FileTailerDatasource" must {

    "properly handle initial cursor in the following scenarios" must {

      "given current.log, three blocks, one read, new instance must read a second block" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(1, _))
          waitAndCheck {
            expectSomeEvents(1, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, two read, new instance must read a third block" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(2, _))
          expectSomeEvents(2, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(1, _))
          waitAndCheck {
            expectSomeEvents(1, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
        }
      }

      "given current.log, three blocks, three read, new instance must not produce any messages" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(3, _))
          expectSomeEvents(3, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(1, _))
          waitAndCheck {
            expectNoEvents(ReceivedMessageAtSink)
          }
        }
      }

      "given current.log, three blocks, three read, new instance must not produce any messages until new input provided" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(3, _))
          expectSomeEvents(3, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runWithExistingFile { f =>
          flowCtx.foreach(sinkProduceDemand(1, _))
          waitAndCheck {
            expectNoEvents(ReceivedMessageAtSink)
          }

          f.write("DDDDD")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "DDDDD")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> "CCC")

        }
      }


      "given current.log, three blocks, one read, rolled, extra input into main, new instance must read a second block" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.rollGz("current-1.gz")
          f.write("DDDDD")
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(1, _))
          waitAndCheck {
            expectSomeEvents(1, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, extra input into main, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.rollGz("current-1.gz")
          f.write("DDDDD")
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(3, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "DDDDD")
        }
      }

      "given current.log, three blocks, one read, rolled, another read, extra input into main, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("DDDDD")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(2, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "DDDDD")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize + "GGG")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }


      "given current.log, three blocks, one read, rolled, lots of extra input into main, another read, roll, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize + "GGG")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.rollGz("current-2.gz")
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }


      "given current.log, three blocks, one read, rolled, lots of extra input into main, roll, another read, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize + "GGG")
          f.rollGz("current-2.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, lots of extra input into main, roll, extra into main, another read, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          f.write("GGG")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, lots of extra input into main, roll, another read, extra into main, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.write("GGG")
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }


      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, roll, extra into main, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          f.write("GGG")
        }

        clearInstance()

        runBare {
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(5, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "CCC")
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, roll, extra into main, current-1 gone, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          f.write("GGG")
        }

        clearInstance()

        runWithExistingFile { f=>
          f.delete("current-1.gz")
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(4, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, roll, extra into main, current-1 and current-2 gone, new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          clearEvents()
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          f.write("GGG")
          waitAndCheck {
            expectNoEvents(ReceivedMessageAtSink)
          }
        }

        clearInstance()

        runWithExistingFile { f=>
          f.delete("current-1.gz")
          f.delete("current-2.gz")
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(1, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
      }

      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, roll, extra into main, current-1 gone (before instance started), new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          clearEvents()
          f.write("GGG")
          f.delete("current-1.gz")
          waitAndCheck {
            expectNoEvents(ReceivedMessageAtSink)
          }
        }

        clearInstance()

        runWithExistingFile { f=>
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(4, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("D" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("E" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> ("F" * testBlockSize))
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }

        override def config: JsValue = super.config.set(__ \ CfgFInactivityThresholdMs -> JsNumber(500))

      }

      "given current.log, three blocks, one read, rolled, another read, lots of extra input into main, roll, extra into main, current-1 and current-2 gone (before instance started), new instance must read remainder if there is enough demand" in new EmptyDirWithoutDemand {
        runWithNewFile { f =>
          f.write("A" * testBlockSize + "B" * testBlockSize + "CCC")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          clearEvents()
          f.rollGz("current-1.gz")
          flowCtx.foreach(sinkProduceDemand(1, _))
          expectSomeEvents(1, ReceivedMessageAtSink)
          state = Some(Json.parse(locateLastEventFieldValue(ReceivedMessageAtSink, "Cursor").asInstanceOf[String]))
          clearEvents()
          f.write("D" * testBlockSize + "E" * testBlockSize + "F" * testBlockSize)
          f.rollGz("current-2.gz")
          f.write("GGG")
          f.delete("current-1.gz")
          f.delete("current-2.gz")
          waitAndCheck {
            expectNoEvents(ReceivedMessageAtSink)
          }
        }

        clearInstance()

        runWithExistingFile { f=>
          flowCtx.foreach(sinkProduceDemand(10, _))
          waitAndCheck {
            expectSomeEvents(1, ReceivedMessageAtSink)
          }
          expectSomeEvents(ReceivedMessageAtSink, 'Value --> "GGG")
          expectNoEvents(ReceivedMessageAtSink, 'Value --> ("B" * testBlockSize))
        }
        override def config: JsValue = super.config.set(__ \ CfgFInactivityThresholdMs -> JsNumber(500))
      }



    }


  }
  
}

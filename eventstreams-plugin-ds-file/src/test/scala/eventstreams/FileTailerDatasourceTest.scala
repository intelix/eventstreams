package eventstreams

import akka.actor.{ActorSystem, Props}
import eventstreams.core.BuilderFromConfig
import eventstreams.ds.plugins.filetailer.FileTailerConstants._
import eventstreams.ds.plugins.filetailer.{FileTailerConstants, FileTailerDatasource}
import eventstreams.support.{ActorTestContext, BuilderFromConfigTestContext, FlowPublisherTestContext}
import play.api.libs.json.{JsValue, Json}

class FileTailerDatasourceTest(_system: ActorSystem)
  extends ActorTestContext(_system)
  with FlowPublisherTestContext with TempFolder {


  def this() = this(ActorSystem("TestSystem"))

  trait WithDatasourceContext extends BuilderFromConfigTestContext {
    def withDatasourceFlow(f: TestFlowFunc) = {
      shouldBuild { instr =>
        withFlow(instr) { ctx => f(ctx)}
      }
    }
  }


  trait WithBasicConfig extends WithDatasourceContext {

    override def builder: BuilderFromConfig[Props] = new FileTailerDatasource()

    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current.log$",
      CfgFRolledPattern -> ".*gz"
    )
  }

  "FileTailerDatasource" must {

    "be built with valid config" in new WithBasicConfig {
      shouldBuild()
    }

    s"not be built if $CfgFDirectory is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj(
        CfgFMainPattern -> "mylog.txt",
        CfgFRolledPattern -> ".*gz"
      )

      shouldNotBuild()
    }

    s"not be built if $CfgFMainPattern is missing" in new WithBasicConfig {
      override def config: JsValue = Json.obj(
        CfgFDirectory -> "f:/tmp/log",
        CfgFRolledPattern -> ".*gz"
      )

      shouldNotBuild()
    }

    "be a new instance when added to the flow" in new WithBasicConfig {
      withDatasourceFlow { implicit ctx =>
        expectSomeEvents(DatasourceInstance)
      }
    }

    "initially be stopped" in new WithBasicConfig {
      withDatasourceFlow { implicit ctx =>
        waitAndCheck { () =>
          expectNoEvents(FileTailerConstants.Starting)
        }
      }
    }

    "propagate demand to the publisher" in new WithBasicConfig {
      withDatasourceFlow { implicit ctx =>
        activateSink()
        expectSomeEvents(FileTailerConstants.NewDemand)
      }
    }

    "when built but stopped" must {

      "activate on request" in new WithBasicConfig {
        withDatasourceFlow { implicit ctx =>
          activateFlow()
          expectSomeEvents(FileTailerConstants.Starting)
        }
      }

      "produce a message" in new WithBasicConfig {
        withDatasourceFlow { implicit ctx =>

          withNewFile("current.log") { f =>
            f.write("line1\n")

            activateFlow()

            expectSomeEvents(FileTailerConstants.MessagePublished)

            clearEvents()

            f.write("line2\n")

            expectSomeEvents(FileTailerConstants.MessagePublished)

            clearEvents()

            f.write("line3\n")
            f.rollGz("current-1.gz")
            expectSomeEvents(FileTailerConstants.MessagePublished)

            clearEvents()

            f.write("line4\n")

            expectSomeEvents(FileTailerConstants.MessagePublished)

            clearEvents()

            (1 to 10) foreach { i =>
              f.write(s"line5-${i+2}\n")
              f.rollGz(s"current-${i+2}.gz")
              f.write(s"line6-${i+2}\nline7-${i+2}\n")

              expectSomeEvents(2, FileTailerConstants.MessagePublished)

              clearEvents()
            }


          }

        }
      }

    }


  }


}

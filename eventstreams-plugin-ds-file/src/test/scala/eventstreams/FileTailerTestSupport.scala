package eventstreams

import _root_.core.events.support.EventAssertions
import akka.actor.Props
import akka.stream.actor.{OneByOneRequestStrategy, RequestStrategy, ZeroRequestStrategy}
import akka.testkit.{TestProbe, TestKit}
import com.typesafe.config.ConfigFactory
import eventstreams.core.BuilderFromConfig
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.ds.plugins.filetailer.FileTailerConstants._
import eventstreams.ds.plugins.filetailer.{FileTailerConstants, FileTailerDatasource}
import eventstreams.support.{StorageStub, BuilderFromConfigTestContext, FlowPublisherTestContext}
import org.scalatest.{BeforeAndAfterEach, Suite}
import play.api.libs.json.{JsValue, Json}

import scala.util.Try

trait FileTailerTestSupport
  extends FlowPublisherTestContext with EventAssertions with TempFolder with BeforeAndAfterEach {
  _: TestKit with Suite =>


  override protected def beforeEach(): Unit = {
    StorageStub.clear()
    super.beforeEach()
  }

  trait WithDatasourceContext extends BuilderFromConfigTestContext {
    var flowCtx: Option[TestFlowCtx] = None

    def sinkRequestStrategy: RequestStrategy = OneByOneRequestStrategy

    def withConfigStorage(f: => Unit) = {
      val cfg = ConfigFactory.parseString( """
    ehub.storage.provider = "eventstreams.support.StorageStub"
                                           """)
      val actor = ConfigStorageActor.start(system, cfg)
      val configMgrActorProbe = TestProbe()

      try {
        f
      } finally {
        system.stop(actor)
        Try {
          configMgrActorProbe expectTerminated actor
        }
      }

    }

    def withDatasourceFlow(f: => Unit) = {
      shouldBuild { instr =>
        withConfigStorage {
          withFlow(instr, sinkRequestStrategy) { ctx =>
            flowCtx = Some(ctx)
            f
          }
        }
      }
    }
  }

  val testBlockSize = 32

  trait WithBasicConfig extends WithDatasourceContext {


    override def id: Option[String] = Some("fileDatasource")

    override def builder: BuilderFromConfig[Props] = new FileTailerDatasource()

    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current[.]log$",
      CfgFRolledPattern -> "current-.+",
      CfgFBlockSize -> testBlockSize
    )
  }


  trait EmptyDirWithDemand extends WithBasicConfig {
    def runWithNewFile(f: OpenFile => Unit) = withDatasourceFlow {
      withNewFile("current.log") { file =>
        flowCtx.foreach(activateFlow)
        expectSomeEvents(FileTailerConstants.Starting)
        clearEvents()
        f(file)
      }
    }
    def runWithExistingFile(f: OpenFile => Unit) = withDatasourceFlow {
      withExistingFile("current.log") { file =>
        flowCtx.foreach(activateFlow)
        expectSomeEvents(FileTailerConstants.Starting)
        clearEvents()
        f(file)
      }
    }

    def runBare(f: => Unit) = withDatasourceFlow {
      flowCtx.foreach(activateFlow)
      expectSomeEvents(FileTailerConstants.Starting)
      clearEvents()
      f
    }
  }

  trait EmptyDirWithDemandDeactivatedStartWithLast extends WithBasicConfig {
    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current[.]log$",
      CfgFRolledPattern -> "current-.+",
      CfgFBlockSize -> testBlockSize
    )

    def run(f: OpenFile => Unit) = withDatasourceFlow {
      withNewFile("current.log") { file =>
        f(file)
      }
    }
  }

  trait EmptyDirWithDemandDeactivatedStartWithFirst extends WithBasicConfig {
    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current[.]log$",
      CfgFRolledPattern -> "current-.+",
      CfgFBlockSize -> testBlockSize,
      CfgFStartWith -> "first"
    )

    def run(f: OpenFile => Unit) = withDatasourceFlow {
      withNewFile("current.log") { file =>
        f(file)
      }
    }
  }

  trait EmptyDirWithoutDemandDeactivatedOrderByNameOnly extends WithBasicConfig {
    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current[.]log$",
      CfgFRolledPattern -> "current-.+",
      CfgFBlockSize -> testBlockSize,
      CfgFStartWith -> "first",
      CfgFFileOrdering -> "name only"
    )

    override def sinkRequestStrategy: RequestStrategy = ZeroRequestStrategy

    def run(f: OpenFile => Unit) = withDatasourceFlow {
      withNewFile("current.log") { file =>
        f(file)
      }
    }
  }

  trait EmptyDirWithoutDemand extends EmptyDirWithDemand {
    override def sinkRequestStrategy: RequestStrategy = ZeroRequestStrategy
  }


}

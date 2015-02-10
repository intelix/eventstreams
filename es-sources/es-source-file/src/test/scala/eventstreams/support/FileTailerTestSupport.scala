package eventstreams.support

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

import core.events.support.EventAssertions
import akka.actor.Props
import akka.stream.actor.{OneByOneRequestStrategy, RequestStrategy, ZeroRequestStrategy}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import eventstreams.core.BuilderFromConfig
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.ds.plugins.filetailer.FileTailerConstants._
import eventstreams.ds.plugins.filetailer.{FileTailerConstants, FileTailerDatasource}
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
    eventstreams.storage.provider = "eventstreams.support.StorageStub"
                                           """)
      val actor = ConfigStorageActor.start(system, cfg)
      val configMgrActorProbe = TestProbe()

      try {
        f
      } finally {
        system.stop(actor)
        Try {
          expectOneOrMoreEvents(ConfigStorageActor.PostStop)
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
        expectOneOrMoreEvents(FileTailerConstants.BecomingActive)
        clearEvents()
        f(file)
      }
    }
    def runWithExistingFile(f: OpenFile => Unit) = withDatasourceFlow {
      withExistingFile("current.log") { file =>
        flowCtx.foreach(activateFlow)
        expectOneOrMoreEvents(FileTailerConstants.BecomingActive)
        clearEvents()
        f(file)
      }
    }

    def runBare(f: => Unit) = withDatasourceFlow {
      flowCtx.foreach(activateFlow)
      expectOneOrMoreEvents(FileTailerConstants.BecomingActive)
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

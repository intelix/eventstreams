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

import core.sysevents.support.EventAssertions
import akka.actor.Props
import akka.stream.actor.{OneByOneRequestStrategy, RequestStrategy, ZeroRequestStrategy}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import eventstreams.BuilderFromConfig
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.sources.filetailer.FileTailerConstants._
import eventstreams.sources.filetailer.{FileTailerConstants, FileTailerEventsource}
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

  trait WithEventsourceContext extends BuilderFromConfigTestContext {
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

    def withEventsourceFlow(f: => Unit) = {
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

  trait WithBasicConfig extends WithEventsourceContext {


    override def id: Option[String] = Some("fileEventsource")

    override def builder: BuilderFromConfig[Props] = new FileTailerEventsource()

    override def config: JsValue = Json.obj(
      CfgFDirectory -> tempDirPath,
      CfgFMainPattern -> "current[.]log$",
      CfgFRolledPattern -> "current-.+",
      CfgFBlockSize -> testBlockSize
    )
  }


  trait EmptyDirWithDemand extends WithBasicConfig {
    def runWithNewFile(f: OpenFile => Unit) = withEventsourceFlow {
      withNewFile("current.log") { file =>
        flowCtx.foreach(activateFlow)
        expectOneOrMoreEvents(FileTailerConstants.BecomingActive)
        clearEvents()
        f(file)
      }
    }
    def runWithExistingFile(f: OpenFile => Unit) = withEventsourceFlow {
      withExistingFile("current.log") { file =>
        flowCtx.foreach(activateFlow)
        expectOneOrMoreEvents(FileTailerConstants.BecomingActive)
        clearEvents()
        f(file)
      }
    }

    def runBare(f: => Unit) = withEventsourceFlow {
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

    def run(f: OpenFile => Unit) = withEventsourceFlow {
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

    def run(f: OpenFile => Unit) = withEventsourceFlow {
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

    def run(f: OpenFile => Unit) = withEventsourceFlow {
      withNewFile("current.log") { file =>
        f(file)
      }
    }
  }

  trait EmptyDirWithoutDemand extends EmptyDirWithDemand {
    override def sinkRequestStrategy: RequestStrategy = ZeroRequestStrategy
  }


}

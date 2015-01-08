package eventstreams.support

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config._
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import org.scalatest.{BeforeAndAfterEach, Suite, Tag}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Try

trait MultiActorSystemTestContextEvents extends ComponentWithBaseEvents {
  override def componentId: String = "Test.ActorSystem"
  val ActorSystemCreated = 'ActorSystemCreated.info
  val ActorSystemTerminated = 'ActorSystemTerminated.info
}

trait ActorSystemWrapper {
  def underlyingSystem: ActorSystem
  def config: Config
  def start(props: Props, id: String): ActorRef
}

trait MultiActorSystemTestContext extends BeforeAndAfterEach with MultiActorSystemTestContextEvents with WithEventPublisher {
  self: Suite =>

  object OnlyThisTest extends Tag("OnlyThisTest")

  case class Wrapper(config: Config, underlyingSystem: ActorSystem, id: String) extends ActorSystemWrapper {
    private var actors = List[ActorRef]()
    override def start(props: Props, id: String): ActorRef = {
      val newActor = underlyingSystem.actorOf(props, id)
      actors = actors :+ newActor
      newActor
    }
    def stop() = Try {
      val startCheckpoint = System.nanoTime()
      actors.foreach(underlyingSystem.stop)
      underlyingSystem.shutdown()
      underlyingSystem.awaitTermination(60.seconds)
      ActorSystemTerminated >> ('Name -> id, 'TerminatedInMs -> (System.nanoTime() - startCheckpoint)/1000000)
    }
  }


  override protected def beforeEach(): Unit = {
    StorageStub.clear()
    LoggerFactory.getLogger("testseparator").debug("\n" * 3 + "-" * 120)
    super.beforeEach()
  }


  def configs: Map[String, Config]
  
  private var systems = Map[String, Wrapper]()

  
  def withSystem[T](configName: String)(f: ActorSystemWrapper => T): T = f(systems.get(configName) match {
    case None =>
      val config = configs.get(configName).get
      val sys = Wrapper(config, ActorSystem("engine", config), "engine")
      ActorSystemCreated >> ('Name -> "engine", 'ConfigName -> configName)
      systems = systems + (configName -> sys)
      sys
    case Some(x) => x
  })
  
  def destroySystem(name: String) = {
    systems.get(name).foreach(_.stop())
    systems = systems - name
    
  }
  
  private def destroyAll() = {
    systems.values.foreach(_.stop())
    systems = Map()
  }
  
  override protected def afterEach(): Unit = {
    LoggerFactory.getLogger("testseparator").debug(" " * 10 + "~" * 40 + " test finished " + "~" * 40)
    destroyAll()
    super.afterEach()
  }
}

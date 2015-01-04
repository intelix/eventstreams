package eventstreams.agent.support

import akka.actor.{ActorRef, Props, ActorSystem}
import com.typesafe.config._
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.support.{StorageStub2, StorageStub1}
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Try

trait MultiActorSystemTestContextEvents extends ComponentWithBaseEvents {
  override def componentId: String = "Test.ActorSystem"
  val ActorSystemCreated = 'ActorSystemCreated.info
  val ActorSystemTerminated = 'ActorSystemTerminated.info
}

trait ActorSystemWrapper {
  def start(props: Props, id: String): ActorRef
}

trait MultiActorSystemTestContext extends BeforeAndAfterEach with MultiActorSystemTestContextEvents with WithEventPublisher {
  self: Suite =>
  
  case class Wrapper(underlyingSystem: ActorSystem, id: String) extends ActorSystemWrapper {
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
      ActorSystemTerminated >> ('Name --> id, 'TerminatedInMs --> (System.nanoTime() - startCheckpoint)/1000000)
    }
  }


  override protected def beforeEach(): Unit = {
    StorageStub1.clear()
    StorageStub2.clear()
    LoggerFactory.getLogger("testseparator").debug("\n" * 3 + "-" * 120)
    super.beforeEach()
  }


  def configs: Map[String, Config]
  
  private var systems = Map[String, Wrapper]()

  private def build(name: String) = configs.get(name) match {
    case None => ActorSystem(name)
    case Some(c) => ActorSystem(name, c)
  }
  
  def withSystem(name: String)(f: ActorSystemWrapper => Unit) = f(systems.get(name) match {
    case None => 
      val underlying = build(name)
      val sys = Wrapper(underlying, name)
      ActorSystemCreated >> ('Name --> name, 'Instance --> underlying)
      systems = systems + (name -> sys)
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

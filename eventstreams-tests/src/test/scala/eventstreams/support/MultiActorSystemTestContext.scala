package eventstreams.support

import akka.actor.{Terminated, ActorRef, ActorSystem, Props}
import com.typesafe.config._
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import core.events.support.EventAssertions
import eventstreams.core.actors.ActorWithComposableBehavior
import eventstreams.core.components.routing.MessageRouterActor
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite, Tag}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Try

trait MultiActorSystemTestContextEvents extends ComponentWithBaseEvents {
  override def componentId: String = "Test.ActorSystem"
  val ActorSystemCreated = 'ActorSystemCreated.trace
  val ActorSystemTerminated = 'ActorSystemTerminated.trace
  val AllActorsTerminated = 'AllActorsTerminated.trace
}

trait ActorSystemWrapper {
  def underlyingSystem: ActorSystem
  def config: Config
  def start(props: Props, id: String): ActorRef
  def actorSelection(id: String) = underlyingSystem.actorSelection(id)
  def rootUserActorSelection(id: String) = actorSelection(s"/user/$id")
}

private case class Watch(ref: ActorRef)
private trait WatcherEvents extends ComponentWithBaseEvents {
  override def componentId: String = "Test.Watcher"
  val WatchedActorGone = 'WatchedActorGone.trace
  val AllWatchedActorsGone = 'AllWatchedActorsGone.trace
}
private object WatcherActor extends WatcherEvents {
  def props = Props(new WatcherActor())
}
private class WatcherActor extends ActorWithComposableBehavior with WatcherEvents with WithEventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior

  var watched = Set[ActorRef]()

  def handler: Receive = {
    case Watch(ref) =>
      watched = watched + ref
      context.watch(ref)
    case Terminated(ref) =>
      watched = watched match  {
        case w if w contains ref =>
          WatchedActorGone >> ('Ref -> ref, 'Path -> ref.path.toSerializationFormat)
          if (w.size == 1) AllWatchedActorsGone >> ()
          w - ref
        case w => w
      }

  }
}

trait MultiActorSystemTestContext extends BeforeAndAfterEach with MultiActorSystemTestContextEvents with WithEventPublisher {
  self: Suite with ActorSystemManagement with EventAssertions =>

  object OnlyThisTest extends Tag("OnlyThisTest")

  case class Wrapper(config: Config, underlyingSystem: ActorSystem, id: String) extends ActorSystemWrapper {
    private var actors = List[ActorRef]()
    private val watcher = underlyingSystem.actorOf(WatcherActor.props)
    override def start(props: Props, id: String): ActorRef = {
      val newActor = underlyingSystem.actorOf(props, id)
      actors = actors :+ newActor
      newActor
    }
    def stop() = Try {
      val startCheckpoint = System.nanoTime()
      stopActors()
      underlyingSystem.shutdown()
      underlyingSystem.awaitTermination(60.seconds)
      ActorSystemTerminated >> ('Name -> id, 'TerminatedInMs -> (System.nanoTime() - startCheckpoint)/1000000)
    }
    def stopActors() = Try {
      val startCheckpoint = System.nanoTime()
      actors.foreach { a =>
        watcher ! Watch(a)
      }
      actors.foreach { a =>
        underlyingSystem.stop(a)
      }
      expectSomeEventsWithTimeout(30000, WatcherActor.AllWatchedActorsGone)
      clearComponentEvents(WatcherActor)
      AllActorsTerminated >> ('TerminatedInMs -> (System.nanoTime() - startCheckpoint)/1000000)
    }
  }


  override protected def beforeEach(): Unit = {
    StorageStub.clear()
    LoggerFactory.getLogger("testseparator").debug("\n" * 3 + "-" * 120)
    super.beforeEach()
  }


  def configs: Map[String, Config]
  
  private var systems = Map[String, Wrapper]()

  def getSystem(configName: String) = systems.get(configName) match {
    case None =>
      val config = configs.get(configName).get
      val sys = Wrapper(config, ActorSystem("engine", config), "engine")
      ActorSystemCreated >> ('Name -> "engine", 'ConfigName -> configName)
      systems = systems + (configName -> sys)
      sys
    case Some(x) => x
  }
  
  def withSystem[T](configName: String)(f: ActorSystemWrapper => T): T = f(getSystem(configName))
  
  def destroySystem(name: String) = {
    systems.get(name).foreach(_.stop())
    systems = systems - name
    
  }

  def destroyAllSystems() = {
    systems.values.foreach(_.stop())
    systems = Map()
  }

  def destroyAllActors() = {
    systems.values.foreach(_.stopActors())
  }

  override protected def afterEach(): Unit = {
    LoggerFactory.getLogger("testseparator").debug(" " * 10 + "~" * 40 + " test finished " + "~" * 40)
    super.afterEach()
  }
}

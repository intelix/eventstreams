package eventstreams.support

import akka.actor.{ActorRef, ActorSystem, Props}
import core.events.Event
import core.events.support.EventAssertions
import eventstreams.core.Utils
import org.scalatest.Suite

trait ActorSystemTestContext extends EventAssertions {
  self: Suite =>

  private var system: Option[ActorSystem] = None

  def buildSystem: ActorSystem

  def start(props: Props, id: String = Utils.generateShortUUID) = system.map(_.actorOf(props, id))
  def stop(actor: Option[ActorRef], terminationEvent: Event) = system.flatMap { s =>
    actor.foreach { a =>
      s.stop(a)
      expectOneOrMoreEvents(terminationEvent)
    }
    None
  }

  def startSystem() = {
    system = Some(buildSystem)
    super.beforeAll()
  }

  def withSystem(f: ActorSystem => Unit) = system.foreach(f)

}

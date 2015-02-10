package eventstreams.core.actors

import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Stop

import scalaz.Scalaz._
import scalaz._

trait StoppableEvents extends ComponentWithBaseEvents {
  val ActorStopped = 'ActorStopped.info
}

trait Stoppable extends ActorWithComposableBehavior with StoppableEvents {
  this: WithEventPublisher =>

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def stop(reason: Option[String]) = {
    context.stop(self)
    ActorStopped >> ('Reason -> (reason | "none given"), 'Actor -> self)
  }

  private def handler: Receive = {
    case Stop(reason) => stop(reason)
  }

}

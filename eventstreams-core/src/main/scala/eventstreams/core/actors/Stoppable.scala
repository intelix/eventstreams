package eventstreams.core.actors

import akka.actor.Actor
import akka.stream.actor.ActorSubscriberMessage.{OnError, OnComplete}
import core.events.EventOps.{symbolToEventField, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Stop

import scalaz._
import Scalaz._

trait StoppableEvents extends ComponentWithBaseEvents {
  val ActorStopped = 'ActorStopped.info
}

trait Stoppable extends ActorWithComposableBehavior with StoppableEvents {
  this: WithEventPublisher =>

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def stop(reason: Option[String]) = {
    context.stop(self)
    ActorStopped >> ('Reason --> (reason | "none given"), 'Actor --> self)
  }

  private def handler: Receive = {
    case Stop(reason) => stop(reason)
  }

}

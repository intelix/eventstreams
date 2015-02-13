package eventstreams.core.actors

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.Stop

import scalaz.Scalaz._

trait StoppableSysevents extends ComponentWithBaseSysevents {
  val ActorStopped = 'ActorStopped.info
}

trait Stoppable extends ActorWithComposableBehavior with StoppableSysevents {
  this: WithSyseventPublisher =>

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def stop(reason: Option[String]) = {
    context.stop(self)
    ActorStopped >> ('Reason -> (reason | "none given"), 'Actor -> self)
  }

  private def handler: Receive = {
    case Stop(reason) => stop(reason)
  }

}

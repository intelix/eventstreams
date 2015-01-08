package eventstreams.support

import akka.actor.{ActorSystem, ActorRef, Props}
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{BaseActorEvents, ActorWithComposableBehavior}
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.Subscribe

private case class SubscribeTo(subj: Any)

trait SubscriberStubEvents extends ComponentWithBaseEvents with BaseActorEvents {
  val SubscriptionMessageReceived = 'MessageReceived.info
}

trait SubscriberStubActor1 extends SubscriberStubEvents {
  private var actor: Option[ActorRef] = None
  private def props = Props(new SubscriberStubActor)
  override def componentId: String = "Test.MsgSubscriber1"
  def startMessageSubscriber(sys: ActorSystemWrapper) = {
    actor = Some(sys.start(props, "msgSubscriber1"))
  }
  def subscribeTo(subj: Any) = actor.foreach(_ ! SubscribeTo(subj))
}
class SubscriberStubActor extends ActorWithComposableBehavior with SubscriberStubEvents with WithEventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def handler: Receive = {
    case SubscribeTo(subj) => MessageRouterActor.path(context) ! Subscribe(self, subj)
    case x => SubscriptionMessageReceived >> ('Message -> x)
  }
}


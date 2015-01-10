package eventstreams.support

import akka.actor.{ActorSystem, ActorRef, Props}
import akka.cluster.Cluster
import core.events.EventOps.{stringToEventOps, symbolToEventOps}
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithClusterAwareness, BaseActorEvents, ActorWithComposableBehavior}
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.Subscribe

private case class SubscribeTo(subj: Any)

trait SubscribingComponentStubEvents extends ComponentWithBaseEvents with BaseActorEvents {
  val SubscriptionMessageReceived = "SubscriptionMessageReceived".info
  override def componentId: String = "Test.SubscribingComponentStub"
}

trait SubscribingComponentStub extends SubscribingComponentStubEvents {
  private def props(instanceId: String) = Props(new SubscribingComponentStubActor(instanceId))
  private def startMessageSubscriber(sys: ActorSystemWrapper, id: String) = {
    sys.start(props(id), id)
  }
  private def subscribeFrom(sys: ActorSystemWrapper, id: String, subj: Any) = sys.rootUserActorSelection(id) ! SubscribeTo(subj)

  def startMessageSubscriberN(sys: ActorSystemWrapper, c: Int) = startMessageSubscriber(sys, "subscriberStub" + c)
  def startMessageSubscriber1(sys: ActorSystemWrapper) = startMessageSubscriberN(sys, 1)
  def startMessageSubscriber2(sys: ActorSystemWrapper) = startMessageSubscriberN(sys, 2)

  def subscribeFromN(sys: ActorSystemWrapper, c: Int, subj: Any) = subscribeFrom(sys, "subscriberStub" + c, subj)
  def subscribeFrom1(sys: ActorSystemWrapper, subj: Any) = subscribeFromN(sys, 1, subj)
  def subscribeFrom2(sys: ActorSystemWrapper, subj: Any) = subscribeFromN(sys, 2, subj)

}
class SubscribingComponentStubActor(instanceId: String) extends ActorWithComposableBehavior with SubscribingComponentStubEvents with WithEventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceId -> instanceId)

  def handler: Receive = {
    case SubscribeTo(subj) => MessageRouterActor.path(context) ! Subscribe(self, subj)
    case x => SubscriptionMessageReceived >> ('Message -> x)
  }
}


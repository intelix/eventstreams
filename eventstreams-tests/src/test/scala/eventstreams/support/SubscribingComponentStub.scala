package eventstreams.support

import akka.actor.Props
import core.events.EventOps.stringToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.Utils
import eventstreams.core.actors.{ActorWithComposableBehavior, BaseActorEvents}
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages._
import play.api.libs.json.{Json, JsValue}

import scalaz._
import Scalaz._

private case class SubscribeTo(subj: Any)
private case class SendCommand(subj: Any, data: Option[JsValue])

trait SubscribingComponentStubEvents extends ComponentWithBaseEvents with BaseActorEvents {
  val StaleReceived = "StaleReceived".info
  val UpdateReceived = "UpdateReceived".info
  val CommandOkReceived = "CommandOkReceived".info
  val CommandErrReceived = "CommandErrReceived".info
  val UnknownMessageReceived = "UnknownMessageReceived".info
  override def componentId: String = "Test.SubscribingComponentStub"
}

trait SubscribingComponentStub extends SubscribingComponentStubEvents {
  private def props(instanceId: String) = Props(new SubscribingComponentStubActor(instanceId))
  private def startMessageSubscriber(sys: ActorSystemWrapper, id: String) = {
    sys.start(props(id), id)
  }
  private def subscribeFrom(sys: ActorSystemWrapper, id: String, subj: Any) = sys.rootUserActorSelection(id) ! SubscribeTo(subj)
  private def commandFrom(sys: ActorSystemWrapper, id: String, subj: Any,  data: Option[JsValue]) = sys.rootUserActorSelection(id) ! SendCommand(subj, data)

  def killMessageSubscriberN(sys: ActorSystemWrapper, c: Int) = sys.stopActor(subscriberStubInstanceIdFor(c))
  def killMessageSubscriber1(sys: ActorSystemWrapper) = killMessageSubscriberN(sys, 1)
  def killMessageSubscriber2(sys: ActorSystemWrapper) = killMessageSubscriberN(sys, 2)

  def startMessageSubscriberN(sys: ActorSystemWrapper, c: Int) = startMessageSubscriber(sys, subscriberStubInstanceIdFor(c))
  def startMessageSubscriber1(sys: ActorSystemWrapper) = startMessageSubscriberN(sys, 1)
  def startMessageSubscriber2(sys: ActorSystemWrapper) = startMessageSubscriberN(sys, 2)

  def subscribeFromN(sys: ActorSystemWrapper, c: Int, subj: Any) = subscribeFrom(sys, subscriberStubInstanceIdFor(c), subj)
  def subscribeFrom1(sys: ActorSystemWrapper, subj: Any) = subscribeFromN(sys, 1, subj)
  def subscribeFrom2(sys: ActorSystemWrapper, subj: Any) = subscribeFromN(sys, 2, subj)

  def commandFromN(sys: ActorSystemWrapper, c: Int, subj: Any,  data: Option[JsValue]) = commandFrom(sys, subscriberStubInstanceIdFor(c), subj, data)
  def commandFrom1(sys: ActorSystemWrapper, subj: Any,  data: Option[JsValue]) = commandFromN(sys, 1, subj, data)
  def commandFrom2(sys: ActorSystemWrapper, subj: Any,  data: Option[JsValue]) = commandFromN(sys, 2, subj, data)

  def subscriberStubInstanceIdFor(c: Int) = "subscriberStub" + c
  
}

object SubscribingComponentStub extends SubscribingComponentStub

class SubscribingComponentStubActor(instanceId: String) extends ActorWithComposableBehavior with SubscribingComponentStubEvents with WithEventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceId -> instanceId)

  def handler: Receive = {
    case SubscribeTo(subj) => MessageRouterActor.path(context) ! Subscribe(self, subj)
    case SendCommand(subj, data) =>
      MessageRouterActor.path(context) ! RegisterComponent(ComponentKey(uuid), self)
      MessageRouterActor.path(context) ! Command(self, subj, Some(LocalSubj(ComponentKey(uuid), TopicKey("_"))), data)
    case x : Stale => StaleReceived >> ('Message -> x)
    case x : Update => UpdateReceived >> ('Message -> x, 'Subject -> x.subj, 'Contents -> (x.data ~> 'msg | "n/a"), 'Data -> Json.stringify(x.data))
    case x : CommandErr => CommandErrReceived >> ('Message -> x, 'Subject -> x.subj, 'Contents -> ((x.data #> 'error ~> 'msg) | "n/a"))
    case x : CommandOk => CommandOkReceived >> ('Message -> x, 'Subject -> x.subj, 'Contents -> ((x.data #> 'ok ~> 'msg) | "n/a"))
    case x => UnknownMessageReceived >> ('Message -> x)
  }
}


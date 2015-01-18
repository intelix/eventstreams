package eventstreams.support

import akka.actor.{ActorRef, Props}
import core.events.EventOps.stringToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithComposableBehavior, BaseActorEvents, RouteeActor, RouteeEvents}
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.core.{Fail, OK}
import play.api.libs.json.{JsValue, Json}

import scalaz.{-\/, \/, \/-}

private case class UpdateTopicData(topic: TopicKey, msg: String)

trait RouteeComponentStubEvents extends ComponentWithBaseEvents with BaseActorEvents with RouteeEvents {
   val RouteeComponentCommandReceived = "RouteeComponentCommandReceived".info
  override def componentId: String = "Test.RouteeComponentStub"
}

trait RouteeComponentStub extends RouteeComponentStubEvents {
  private def props(instanceId: String) = Props(new RouteeComponentStubActor(instanceId))

  private def startRouteeComponentStub(sys: ActorSystemWrapper, id: String) = {
    sys.start(props(id), id)
  }

  private def updateTopicFromRoutee(sys: ActorSystemWrapper, id: String, key: TopicKey, msg: String) = sys.rootUserActorSelection(id) ! UpdateTopicData(key, msg)

  def killRouteeComponentStubN(sys: ActorSystemWrapper, c: Int) = sys.stopActor(RouteeComponentStubOps.routeeIdFor(c))
  def killRouteeComponentStub1(sys: ActorSystemWrapper) = killRouteeComponentStubN(sys, 1)
  def killRouteeComponentStub2(sys: ActorSystemWrapper) = killRouteeComponentStubN(sys, 2)


  def startRouteeComponentStubN(sys: ActorSystemWrapper, c: Int) = startRouteeComponentStub(sys, RouteeComponentStubOps.routeeIdFor(c))

  def startRouteeComponentStub1(sys: ActorSystemWrapper) = startRouteeComponentStubN(sys, 1)

  def startRouteeComponentStub2(sys: ActorSystemWrapper) = startRouteeComponentStubN(sys, 2)

  def updateTopicFromRouteeN(sys: ActorSystemWrapper, c: Int, key: TopicKey, msg: String) = updateTopicFromRoutee(sys, RouteeComponentStubOps.routeeIdFor(c), key, msg)
  def updateTopicFromRoutee1(sys: ActorSystemWrapper, key: TopicKey, msg: String) = updateTopicFromRouteeN(sys, 1, key, msg)
  def updateTopicFromRoutee2(sys: ActorSystemWrapper, key: TopicKey, msg: String) = updateTopicFromRouteeN(sys, 2, key, msg)

}

object RouteeComponentStubOps extends RouteeComponentStubEvents {
  val defaultInstanceId = "routeeStub"

  def routeeIdFor(c: Int) = defaultInstanceId + c.toString
  
  def componentKeyForRouteeStub(instanceId: String) = ComponentKey("provider/" + instanceId)

  def componentKeyForRouteeStub1 = componentKeyForRouteeStub(routeeIdFor(1))
  def componentKeyForRouteeStub2 = componentKeyForRouteeStub(routeeIdFor(2))

}

class RouteeComponentStubActor(instanceId: String)
  extends ActorWithComposableBehavior
  with RouteeComponentStubEvents
  with RouteeActor
  with WithEventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceId -> instanceId)

  def handler: Receive = {
    case UpdateTopicData(topic, msg) => topic !! Some(Json.obj("msg" -> msg))
  }


  override def processTopicSubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = topic match {
    case TopicKey("withresponse") => topic !! Some(Json.obj("msg" -> "response"))
    case TopicKey("withunsupportedresponse") => topic !! "response"
    case _ => ()
  }


  override def processTopicCommand(sourceRef: ActorRef, topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = {
    RouteeComponentCommandReceived >> ('Command -> topic.key, 'Data -> maybeData)

    topic match {
      case TopicKey("okwithmessage") =>
        \/-(OK(message = Some("message")))
      case TopicKey("ok") => \/-(OK())
      case TopicKey("failwithmessage") => -\/(Fail(message = Some("message")))
      case TopicKey("fail") => -\/(Fail())
    }
  }

  override def key: ComponentKey = RouteeComponentStubOps.componentKeyForRouteeStub(instanceId)
}

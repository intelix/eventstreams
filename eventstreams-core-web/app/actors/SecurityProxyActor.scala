package actors

import akka.actor.{ActorRefFactory, Props}
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.{OK, Fail, NowProvider}
import eventstreams.core.actors._
import eventstreams.core.messages._
import play.api.libs.json.{Json, JsValue}

import scalaz._
import Scalaz._

trait SecurityProxyEvents extends ComponentWithBaseEvents with BaseActorEvents with SubjectSubscriptionEvents {
  override def componentId: String = "Actor.SecurityProxy"

  val SecurityViolation = 'SecurityViolation.warn
}

object SecurityProxyActor extends SecurityProxyEvents {
  def start(token: String)(implicit f: ActorRefFactory) = f.actorOf(Props(new SecurityProxyActor(token)), token)
}

class SecurityProxyActor(token: String)
  extends ActorWithComposableBehavior
  with RouteeActor
  with NowProvider
  with SecurityProxyEvents
  with WithEventPublisher
  with ActorWithTicks {

  override def key: ComponentKey = ComponentKey(token)

  val proxy = RouterActor.path

  override def preStart(): Unit = {
    super.preStart()
    proxy ! Subscribe(self, RemoteRoleSubj("auth", LocalSubj(ComponentKey("auth"), TopicKey(token))))
  }

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = topic match {
    case TopicKey("login") =>
      for (
        user <- maybeData ~> 'u \/> Fail(message = Some("Invalid username or password"));
        passw <- maybeData ~> 'p \/> Fail(message = Some("Invalid username or password"))
      ) yield {
        proxy ! Command(
          RemoteRoleSubj("auth", LocalSubj(ComponentKey("auth"), topic)), 
          replyToSubj, 
          Some(Json.stringify(Json.obj(
            "u" -> user, "p" -> passw, "routeKey" -> token
          ))))
        OK()
      }
  }

  def handler: Receive = {
    case Update(_, data, _) =>
      logger.error(s"!>>>>>> update from auth $data")
  }


}

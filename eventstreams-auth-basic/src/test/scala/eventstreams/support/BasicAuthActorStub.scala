package eventstreams.support

import akka.actor.{ActorRefFactory, Props}
import core.events.WithEventPublisher
import eventstreams.auth.{UserRoleManager, UserManager, BasicAuthEvents}
import eventstreams.core.Tools.configHelper
import eventstreams.core.{OK, Fail, NowProvider}
import eventstreams.core.actors.{ActorTools, ActorWithTicks, RouteeActor, ActorWithComposableBehavior}
import eventstreams.core.messages.{ComponentKey, TopicKey}
import eventstreams.model.{FunctionPermission, SecuredDomain, DomainPermissions, RolePermissions}
import play.api.libs.json.{Json, JsValue}

import scalaz._
import Scalaz._


object BasicAuthActorStub {
  def props(id: String) = Props(new BasicAuthActorStub(id))
  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class BasicAuthActorStub(id: String)
  extends ActorWithComposableBehavior
  with RouteeActor
  with NowProvider
  with BasicAuthEvents
  with WithEventPublisher
  with ActorWithTicks {

  override def key: ComponentKey = ComponentKey(id)


  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = topic match {
    case TopicKey("auth_cred") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        TopicKey(routeKey) !! Json.obj(
          "token" -> "token",
          "allow" -> true,
          "permissions" -> Seq(RolePermissions(Seq(DomainPermissions(SecuredDomain("*"), Seq(FunctionPermission(".+"))))))
        )
        OK().right
      }.getOrElse(Fail().left)

    case TopicKey("auth_token") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        TopicKey(routeKey) !! Json.obj(
          "token" -> "token",
          "allow" -> true,
          "permissions" -> Seq(RolePermissions(Seq(DomainPermissions(SecuredDomain("*"), Seq(FunctionPermission(".+"))))))
        )
        OK().right
      }.getOrElse(Fail().left)
  }


}

package eventstreams.support

import _root_.core.sysevents.WithSyseventPublisher
import akka.actor.{ActorRefFactory, Props}
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.auth.{AuthSysevents, DomainPermissions, FunctionPermission, RolePermissions, SecuredDomain}
import eventstreams.core.actors.{ActorTools, ActorWithComposableBehavior, ActorWithTicks, RouteeActor}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._


object AuthActorStub {
  def props(id: String) = Props(new AuthActorStub(id))
  def start(id: String)(implicit f: ActorRefFactory) = f.actorOf(props(id), ActorTools.actorFriendlyId(id))
}


class AuthActorStub(id: String)
  extends ActorWithComposableBehavior
  with RouteeActor
  with NowProvider
  with AuthSysevents
  with WithSyseventPublisher
  with ActorWithTicks {

  override def key: ComponentKey = ComponentKey(id)


  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case TopicKey("auth_cred") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        TopicKey(routeKey) !! Json.obj(
          "token" -> "token",
          "allow" -> true,
          "permissions" -> Seq(RolePermissions(Seq(DomainPermissions(SecuredDomain("*"), Seq(FunctionPermission(".+"))))))
        )
        OK()
      }.getOrElse(Fail())

    case TopicKey("auth_token") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        TopicKey(routeKey) !! Json.obj(
          "token" -> "token",
          "allow" -> true,
          "permissions" -> Seq(RolePermissions(Seq(DomainPermissions(SecuredDomain("*"), Seq(FunctionPermission(".+"))))))
        )
        OK()
      }.getOrElse(Fail())
  }


}

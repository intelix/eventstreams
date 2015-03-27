package actors

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, ActorRefFactory, Props}
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors._
import eventstreams.auth._
import play.api.libs.json.Json

import scala.util.matching.Regex
import scalaz.Scalaz._

trait SecurityProxySysevents extends ComponentWithBaseSysevents with BaseActorSysevents with SubjectSubscriptionSysevents {
  override def componentId: String = "Private.SecurityProxy"

  val SecurityViolation = 'SecurityViolation.warn
  val Permissions = 'Permissions.info
}

case class Untrusted(msg: Any)

case class Trusted(msg: Any)

case class TokenAuth(token: String)
case class CredentialsAuth(user: String, passwordHash: String)
case class AuthorizationUpdate(msg: String)

object SecurityProxyActor extends SecurityProxySysevents {
  def start(token: String, clientRef: ActorRef)(implicit f: ActorRefFactory) = f.actorOf(Props(new SecurityProxyActor(token, clientRef)), token)
}

class SecurityProxyActor(token: String, clientRef: ActorRef)
  extends ActorWithComposableBehavior
  with RouteeActor
  with NowProvider
  with SecurityProxySysevents
  with WithSyseventPublisher
  with ActorWithTicks {


  override def entityId: String = token

  case class PatternGroup(patterns: Seq[Regex]) {
    def permitted(key: String) = patterns.exists(_.findFirstMatchIn(key).isDefined)
  }

  val proxy = BridgeActor.path

  val privateAllowedPattern: Seq[PatternGroup] =
    List(PatternGroup(List(token.r, "unsecured_".r)))
  var patterns: Seq[PatternGroup] = Seq()

  override def preStart(): Unit = {
    super.preStart()
    proxy ! Subscribe(self, RemoteAddrSubj("~auth", LocalSubj(ComponentKey("auth"), TopicKey(token))))
  }

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  private def localSubj(subj: Any) = subj match {
    case RemoteAddrSubj(_, local) => local
    case x: LocalSubj => x
  }

  private def allowed(msg: Any) = msg match {
    case x: ServiceSubscriptionMessage[_] => hasPermissionFor(localSubj(x.subj))
    case _ => true
  }

  private def hasPermissionFor(subj: LocalSubj) = {
    val key = subj.component.key + "#" + subj.topic.key
    privateAllowedPattern.exists(_.permitted(key)) || patterns.exists(_.permitted(key))
  }


  def handler: Receive = {
    case CredentialsAuth(u,p) =>
      proxy ! Command(
        RemoteAddrSubj("~auth", LocalSubj(ComponentKey("auth"), TopicKey("auth_cred"))),
        None,
        Some(Json.stringify(Json.obj(
          "u" -> u, "p" -> p, "routeKey" -> token
        ))))
    case TokenAuth(t) =>
      proxy ! Command(
        RemoteAddrSubj("~auth", LocalSubj(ComponentKey("auth"), TopicKey("auth_token"))),
        None,
        Some(Json.stringify(Json.obj(
          "t" -> t, "routeKey" -> token
        ))))
    case Trusted(msg) => proxy ! msg
    case Untrusted(msg) => if (allowed(msg)) proxy ! msg else SecurityViolation >> ('Message -> msg)
    case Update(_, data, _) =>
      val json = Json.parse(data)
      val permissions = (json #> 'permissions).flatMap(_.asOpt[Seq[RolePermissions]]) | Seq()

      Permissions >> ('Set ->
        permissions.map {
          rp =>
            rp.domainPermissions.flatMap(_.permissions.map(_.topicPattern)).mkString("(", ",", ")")
        }.mkString("{", ";", "}")
        )

      patterns = permissions.map { p =>
        PatternGroup(p.domainPermissions.flatMap { next => next.permissions.map(_.topicPattern.r)})
      }

      clientRef ! AuthorizationUpdate(Json.stringify(Json.obj("allow" -> (json ?> 'allow | false), "token" -> (json ~> 'token | ""), "permissions" -> (json #> 'permissions | Json.arr()))))
  }


}

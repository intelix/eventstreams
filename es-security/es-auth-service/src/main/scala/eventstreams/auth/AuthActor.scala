package eventstreams.auth

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.cluster.Cluster
import com.typesafe.config.Config
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors._
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration._
import scalaz.Scalaz._

trait AuthSysevents extends ComponentWithBaseSysevents with BaseActorSysevents with SubjectSubscriptionSysevents {
  override def componentId: String = "Auth.Manager"

  val LoginAttempt = 'LoginAttempt.info
  val LoginSuccessful = 'LoginSuccessful.info
  val LoginFailed = 'LoginFailed.warn


}

object AuthActor extends AuthSysevents {
  val id = "auth"
}

case class SessionMeta(token: String, user: String, routes: Set[String], createdTs: Long = System.currentTimeMillis(), inactiveSince: Option[Long] = None)

case class AvailableUsers(list: List[UserAvailable])

case class AvailableUserRoles(list: List[UserRoleAvailable])

class AuthActor(config: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with RouteeActor
  with NowProvider
  with AuthSysevents
  with WithSyseventPublisher
  with ActorWithTicks {

  var sessionMap = Map[String, SessionMeta]()

  var availableUsers: List[UserAvailable] = List()
  var availableUserRoles: List[UserRoleAvailable] = List()


  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    implicit val c = config
    UserManager.start
    UserRoleManager.start
  }

  override def entityId: String = AuthActor.id

  override def onUnsubscribe: UnsubscribeHandler = super.onUnsubscribe orElse {
    case topic if sessionMap.exists(_._2.routes.contains(topic.key)) =>
      sessionMap = sessionMap.map {
        case (k, v) if v.routes.contains(topic.key) => (k, v.copy(routes = v.routes - topic.key))
        case (k, v) => (k, v)
      }
  }


  def allowSession(meta: SessionMeta) =
    meta.routes.foreach(TopicKey(_) !! Json.obj(
      "token" -> meta.token,
      "allow" -> true,
      "permissions" -> permissionsForUser(meta.user)
    ))


  def disallowSession(routeKeys: Set[String]) =
    routeKeys.foreach(TopicKey(_) !! Json.obj(
      "allow" -> false
    ))


  private def isSuperuser(user: String): Boolean = config.as[Option[String]](s"eventstreams.auth.basic.$user.master-password").isDefined

  private def isValidUser(user: String): Boolean = availableUsers.find(_.name == user) match {
    case Some(x) => true
    case None => isSuperuser(user)
  }

  private def permissionsForUser(user: String): Seq[RolePermissions] =
    if (isSuperuser(user)) {
      Seq(RolePermissions(Seq(DomainPermissions(SecuredDomain("*"), Seq(FunctionPermission(".+"))))))
    } else {
      availableUsers.find(_.name == user).map { u =>
        availableUserRoles.filter { role => u.roles.contains(role.name)}.map(_.permissions).toSeq
      }.getOrElse(Seq())
    }

  private def expectedHashForUser(user: String): Option[String] = availableUsers.find(_.name == user) match {
    case Some(x) => x.hash
    case None => config.as[Option[String]](s"eventstreams.auth.basic.$user.master-password")
  }

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case TopicKey("auth_cred") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        LoginAttempt >>('User -> (maybeData ~> 'u | "n/a"), 'Password -> (maybeData ~> 'p | "n/a"))
        (for (
          user <- maybeData ~> 'u;
          passw <- maybeData ~> 'p;
          expected <- expectedHashForUser(user);
          result <- if (expected == passw) Some(user) else None
        ) yield result) match {
          case Some(user) =>
            val meta = sessionMap.getOrElse(user, SessionMeta(shortUUID, user, Set()))
            val newMeta = meta.copy(routes = meta.routes + routeKey, inactiveSince = None)
            sessionMap += newMeta.user -> newMeta
            allowSession(newMeta)
            LoginSuccessful >>()
            OK()
          case _ =>
            LoginFailed >>()
            disallowSession(Set(routeKey))
            Fail(message = Some("Invalid login or password"))
        }
      } | Fail()

    case TopicKey("auth_token") =>
      (maybeData ~> 'routeKey).map { routeKey =>
        LoginAttempt >> ('Token -> (maybeData ~> 't | "n/a"))
        (for (
          token <- maybeData ~> 't;
          meta <- sessionMap.collectFirst { case (k, v) if v.token == token => v}
        ) yield meta) match {
          case Some(meta) =>
            val newMeta = meta.copy(routes = meta.routes + routeKey, inactiveSince = None)
            sessionMap += newMeta.user -> newMeta
            allowSession(newMeta)
            LoginSuccessful >>()
            OK()
          case _ =>
            LoginFailed >>()
            disallowSession(Set(routeKey))
            Fail(message = Some("Invalid login or password"))
        }
      } | Fail()
  }

  override def processTick(): Unit = {
    sessionMap = sessionMap.map {
      case (k, v) if v.routes.isEmpty => v.inactiveSince match {
        case None => k -> v.copy(inactiveSince = Some(now))
        case x => k -> v
      }
      case (k, v) => k -> v
    }.filter {
      case (k, v) => v.routes.nonEmpty || (v.inactiveSince.map(now - _ < 5.minutes.toMillis) | true)
    }
    super.processTick()
  }


  def terminateRemovedUsers(newUsers: List[UserAvailable]) = sessionMap = sessionMap.filter {
    case (k, v) => newUsers.exists(_.name == k) match {
      case false =>
        if (!isSuperuser(k)) {
          disallowSession(v.routes)
          false
        } else true
      case _ => true
    }
  }

  def updateSessionPermissions() = sessionMap.values.foreach(allowSession)


  def handler: Receive = {
    case AvailableUserRoles(entries) =>
      availableUserRoles = entries
      updateSessionPermissions()
    case AvailableUsers(entries) =>
      terminateRemovedUsers(entries)
      availableUsers = entries
      updateSessionPermissions()
  }

}

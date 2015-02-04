package eventstreams.auth

import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import com.typesafe.config.Config
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.{Utils, OK, Fail, NowProvider}
import eventstreams.core.actors._
import eventstreams.core.messages.{TopicKey, LocalSubj, ComponentKey}
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsString, Json, JsValue}

import scalaz._
import Scalaz._

trait BasicAuthEvents extends ComponentWithBaseEvents with BaseActorEvents with SubjectSubscriptionEvents {
  override def componentId: String = "Actor.BasicAuth"

  val LoginSuccessful = 'LoginSuccessful.info
  val LoginFailed = 'LoginFailed.warn
}

//object BasicAuthActor extends ActorObjWithConfig with BasicAuthEvents {
//  def id = "auth"
//
//  def props(implicit config: Config) = Props(new BasicAuthActor())
//}

case class SessionMeta(token: String, user: String, routes: Set[String], createdTs: Long = System.currentTimeMillis())


class BasicAuthActor(id: String, config: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider
  with BasicAuthEvents
  with WithEventPublisher
  with ActorWithTicks {

  var sessionMap = Map[String, SessionMeta]()

  override def preStart(): Unit = {
    super.preStart()
    implicit val c = config
    UserManager.start
    UserRoleManager.start
  }

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = {}

  override def key: ComponentKey = ComponentKey(id)


  override def processTopicUnsubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = {
    sessionMap = sessionMap.map {
      case (k, v) if v.routes.contains(topic.key) => (k, v.copy(routes = v.routes - topic.key))
      case (k, v) => (k, v)
    }
  }

  private def updateAndPublishSessionState(meta: SessionMeta) = {
    val response = Json.obj(
      "token" -> meta.token,
      "state" -> "ok",
      "permissions" -> Json.toJson((config.as[Option[Set[String]]](s"eventstreams.auth.basic.${meta.user}.permissions") | Set()).toSeq)
    )

    sessionMap += meta.user -> meta

    meta.routes.foreach(TopicKey(_) !! response)
  }

  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]): \/[Fail, OK] = topic match {
    case TopicKey("auth_cred") =>
      for (
        routeKey <- maybeData ~> 'routeKey \/> Fail(message = Some("Unable to process request"));
        user <- maybeData ~> 'u \/> Fail(message = Some("Invalid username or password"));
        passw <- maybeData ~> 'p \/> Fail(message = Some("Invalid username or password"));
        expected <- config.as[Option[String]](s"eventstreams.auth.basic.$user.master-password") \/> Fail(message = Some("Invalid username or password"));
        _ <- if (expected == passw) {
          LoginSuccessful >>('User -> user, 'Password -> passw)
          OK().right
        } else {
          LoginFailed >>('User -> user, 'Password -> passw)
          Fail(message = Some("Invalid username or password")).left
        }
      ) yield {
        val meta = sessionMap.getOrElse(user, SessionMeta(shortUUID, user, Set()))
        updateAndPublishSessionState(meta.copy(routes = meta.routes + routeKey))
        OK()
      }
    case TopicKey("auth_token") =>
      for (
        routeKey <- maybeData ~> 'routeKey \/> Fail(message = Some("Unable to process request"));
        token <- maybeData ~> 't \/> Fail(message = Some("Invalid security token"));
        meta <- sessionMap.collectFirst { case (k, v) if v.token == token => v} \/> Fail(message = Some("Invalid security token"))
      ) yield {
        updateAndPublishSessionState(meta.copy(routes = meta.routes + routeKey))
        OK()
      }
  }

  override def processTick(): Unit = {
    sessionMap = sessionMap.filter {
      case (k,v) => v.routes.nonEmpty
    }
    super.processTick()
  }
}

import actors.{LocalClusterAwareActor, RouterActor}
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import play.api._
import play.api.mvc._
import play.libs.Akka
import play.api.mvc.Results._

import scala.concurrent.Future

object Global extends GlobalSettings with scalalogging.StrictLogging {

  private var clusterSystem: Option[ActorSystem] = None

  override def onStart(app: Application): Unit = {

    clusterSystem.foreach(_.shutdown())

    val localSystem = Akka.system()

    implicit val config = ConfigFactory.load("ehub.conf")

    implicit val newClusterSystem = ActorSystem("ehub", config)

    clusterSystem = Some(newClusterSystem)

    implicit val cluster = Cluster(newClusterSystem)


    implicit val ec = newClusterSystem.dispatcher

    val messageRouter = MessageRouterActor.start
    ClusterManagerActor.start

    LocalClusterAwareActor.start(cluster)(localSystem)
    RouterActor.start(messageRouter)(localSystem)

  }

  override def onStop(app: Application): Unit = {
    clusterSystem.foreach(_.shutdown())
    clusterSystem = None
    super.onStop(app)
  }


  // 404 - page not found error
  override def onHandlerNotFound(request: RequestHeader) = Future.successful(
    NotFound(views.html.errors.onHandlerNotFound(request))
  )

  // 500 - internal server error
  override def onError(request: RequestHeader, throwable: Throwable) = Future.successful(
    InternalServerError(views.html.errors.onError(throwable))
  )

  // called when a route is found, but it was not possible to bind the request parameters
  override def onBadRequest(request: RequestHeader, error: String) = Future.successful(
    BadRequest("Bad Request: " + error)
  )
  
//  override def onRouteRequest(request: RequestHeader) = {
//    Routes.routes.lift(request)
//  }



}
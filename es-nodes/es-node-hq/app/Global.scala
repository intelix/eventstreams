/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import actors.{BridgeActor, HQGroupsManager, LocalClusterAwareActor}
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import play.api._
import play.api.mvc.Results._
import play.api.mvc._
import play.libs.Akka

import scala.concurrent.Future

object Global extends GlobalSettings with scalalogging.StrictLogging {

  private var clusterSystem: Option[ActorSystem] = None

  override def onStart(app: Application): Unit = {

    clusterSystem.foreach(_.shutdown())

    val localSystem = Akka.system()

    implicit val config = ConfigFactory.load("eventstreams.conf")

    implicit val newClusterSystem = ActorSystem("hub", config)

    clusterSystem = Some(newClusterSystem)

    implicit val cluster = Cluster(newClusterSystem)


    implicit val ec = newClusterSystem.dispatcher

    val messageRouter = MessageRouterActor.start
    ClusterManagerActor.start

    HQGroupsManager.start
    LocalClusterAwareActor.start(cluster)(localSystem)
    BridgeActor.start(messageRouter)(localSystem)

  }

  override def onStop(app: Application): Unit = {
    clusterSystem.foreach(_.shutdown())
    clusterSystem = None
    super.onStop(app)
  }


  override def onHandlerNotFound(request: RequestHeader) = Future.successful(
    NotFound(views.html.errors.onHandlerNotFound(request))
  )

  override def onError(request: RequestHeader, throwable: Throwable) = Future.successful(
    InternalServerError(views.html.errors.onError(throwable))
  )

  override def onBadRequest(request: RequestHeader, error: String) = Future.successful(
    BadRequest("Bad Request: " + error)
  )
  



}
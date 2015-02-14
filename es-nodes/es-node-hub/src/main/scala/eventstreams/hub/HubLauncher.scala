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

package eventstreams.hub

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.kernel.Bootable
import com.typesafe.config._
import core.sysevents.{LoggerSyseventPublisher, SEvtSystem, SyseventPublisherRef, SyseventSystemRef}
import eventstreams.agent.AgentsManagerActor
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.desktopnotifications.DesktopNotificationsSubscriptionManagerActor
import eventstreams.flows.FlowManagerActor
import eventstreams.gates.GateManagerActor
import eventstreams.retention.RetentionManagerActor
import net.ceedubs.ficus.Ficus._

class HubLauncher extends Bootable {

  implicit val config = ConfigFactory.load(java.lang.System.getProperty("config", "hub.conf"))
  
  SyseventPublisherRef.ref = LoggerSyseventPublisher
  SyseventSystemRef.ref = SEvtSystem("EventStreams.Engine")

  implicit val system = ActorSystem("engine", config)

  implicit val cluster = Cluster(system)


  override def startup(): Unit = {
    
    ClusterManagerActor.start
    ConfigStorageActor.start
    MessageRouterActor.start

    config.as[Option[Set[Config]]]("eventstreams.bootstrap").foreach(_.foreach { conf =>
      for (
        cl <- conf.as[Option[String]]("class");
        id <- conf.as[Option[String]]("id")
      ) system.actorOf(Props(Class.forName(cl), id, config, cluster), id)
    })
  }

  override def shutdown(): Unit = {
    system.shutdown()
  }
}

object HubLauncherApp extends App {
  new HubLauncher().startup()
}
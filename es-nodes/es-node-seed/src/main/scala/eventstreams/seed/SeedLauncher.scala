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

package eventstreams.seed

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.kernel.Bootable
import com.typesafe.config._
import core.sysevents.{LoggerSyseventPublisher, SEvtSystem, SyseventPublisherRef, SyseventSystemRef}
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import net.ceedubs.ficus.Ficus._

class SeedLauncher extends Bootable {

  implicit val config = ConfigFactory.load(java.lang.System.getProperty("config", "seed.conf"))
  
  SyseventPublisherRef.ref = LoggerSyseventPublisher
  SyseventSystemRef.ref = SEvtSystem("EventStreams.Seed")

  implicit val system = ActorSystem("hub", config)

  implicit val cluster = Cluster(system)


  override def startup(): Unit = {
    
    ClusterManagerActor.start
    ConfigStorageActor.start
    MessageRouterActor.start

    config.as[Option[Set[Config]]]("eventstreams.bootstrap").foreach(_.foreach { conf =>
      for (
        cl <- conf.as[Option[String]]("class");
        id <- conf.as[Option[String]]("actor-id")
      ) system.actorOf(Props(Class.forName(cl), config, cluster), id)
    })
  }

  override def shutdown(): Unit = {
    system.shutdown()
  }
}

object SeedLauncherApp extends App {
  new SeedLauncher().startup()
}
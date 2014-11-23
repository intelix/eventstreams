/*
 * Copyright 2014 Intelix Pty Ltd
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

package hq

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import common.storage.ConfigStorageActor
import hq.agents.AgentsManagerActor
import hq.cluster.ClusterManagerActor
import hq.flows.FlowManagerActor
import hq.gates.GateManagerActor
import hq.routing.MessageRouterActor

/**
 * Created by maks on 18/09/14.
 */
object HQLauncher extends App {

  implicit val system = ActorSystem("ehub", ConfigFactory.load("akka-hq.conf"))

  implicit val config = ConfigFactory.load("hq.conf")

  implicit val cluster = Cluster(system)

  ClusterManagerActor.start
  ConfigStorageActor.start
  MessageRouterActor.start
  GateManagerActor.start
  AgentsManagerActor.start
  FlowManagerActor.start


}

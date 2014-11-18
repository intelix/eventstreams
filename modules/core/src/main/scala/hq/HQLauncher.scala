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

import agent.controller.AgentControllerActor
import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import hq.agents.AgentsManagerActor
import hq.flows.FlowManagerActor
import hq.gates.GateManagerActor
import hq.routing.MessageRouterActor

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.inject.internal.ToStringBuilder
import org.elasticsearch.common.settings.ImmutableSettings

/**
 * Created by maks on 18/09/14.
 */
object HQLauncher extends App {

  implicit val system =  ActorSystem("application",ConfigFactory.load("akka-hq.conf"))

  implicit val config = ConfigFactory.load("agent.conf")

  implicit val cluster = Cluster(system)


  MessageRouterActor.start
  GateManagerActor.start
  AgentsManagerActor.start
  FlowManagerActor.start

//  val settings = ImmutableSettings.settingsBuilder().put("cluster.name", "vagrant_elasticsearch").build()
  val client = ElasticClient.remote("localhost", 9300)

  Thread.sleep(3000)

  client.execute { index into "bands/artists" id "hey" fields ("name"->"oh") }.await
//
  Thread.sleep(3000)
//
//  val resp = client.execute { search in "bands/artists" query "oh" }.await
//  println(resp)

  val resp = client.execute { get id "hey" from "bands/artists" }.await
  println(resp.getSourceAsString)

  Thread.sleep(3000)

 println(client.execute { get id "hey" from "bands/artists" }.await.getSourceAsString)

}

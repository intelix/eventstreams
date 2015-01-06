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

package eventstreams.engine

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import core.events.{CtxSystemRef, EventPublisherRef, EvtSystem, LoggerEventPublisher}
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.engine.agents.AgentsManagerActor
import eventstreams.engine.flows.FlowManagerActor
import eventstreams.engine.gates.{RetentionManagerActor, GateManagerActor}
import eventstreams.engine.plugins.SignalSubscriptionManagerActor

/**
 * Created by maks on 18/09/14.
 */
object HQLauncher extends App {

  implicit val config = ConfigFactory.load(java.lang.System.getProperty("config", "hq.conf"))

  EventPublisherRef.ref = LoggerEventPublisher
  CtxSystemRef.ref = EvtSystem("EventStreams.Engine")

  implicit val system = ActorSystem("ehub", config)

  implicit val cluster = Cluster(system)

  ClusterManagerActor.start
  ConfigStorageActor.start
  MessageRouterActor.start
  GateManagerActor.start
  AgentsManagerActor.start
  FlowManagerActor.start

  RetentionManagerActor.start

  SignalSubscriptionManagerActor.start

}

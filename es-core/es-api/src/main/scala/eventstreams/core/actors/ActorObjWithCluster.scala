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

package eventstreams.core.actors

import akka.actor.{ActorRefFactory, Props}
import akka.cluster.Cluster
import com.typesafe.config.Config

trait ActorObjWithCluster extends ActorObj {
  def props(implicit cluster: Cluster, config: Config): Props
  def start(implicit f: ActorRefFactory, cluster: Cluster, config: Config) = f.actorOf(props, id)
}

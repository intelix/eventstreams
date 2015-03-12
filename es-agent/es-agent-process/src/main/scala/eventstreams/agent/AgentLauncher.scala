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

package eventstreams.agent

import akka.actor.ActorSystem
import akka.kernel.Bootable
import com.typesafe.config.ConfigFactory
import eventstreams.core.storage.ConfigStorageActor

class AgentLauncher extends Bootable {

  implicit val config = ConfigFactory.load("agent.conf")

  implicit val system = ActorSystem("Agent", config)
  override def startup(): Unit = {
    ConfigStorageActor.start
    AgentControllerActor.start
  }
  override def shutdown(): Unit = {
    system.shutdown()
  }
}

object AgentLauncherApp extends App {
  new AgentLauncher().startup()
}

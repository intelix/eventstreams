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

package agent.controller.storage

import akka.actor.{ActorRefFactory, Actor, Props}
import com.typesafe.config.Config
import common.actors.{ActorObjWithConfig, ActorObj, ActorWithComposableBehavior}

/**
 * Created by maks on 22/09/2014.
 */
object ConfigStorageActor extends ActorObjWithConfig {
  override val id = "cfgStorage"
  override def props(implicit config: Config) = Props(new ConfigStorageActor())
}

case class TapInstance(tapId: Long, config: String, state: Option[String])

case class TapState(tapId: Long, state: Option[String])

case class TapConfig(tapId: Long, state: String)

case class StoreDatasourceInstance(config: TapInstance)
case class StoreDatasourceConfig(config: TapConfig)
case class StoreDatasourceState(config: TapState)

case class RetrieveConfigFor(tapId: Long)

case class RetrieveConfigForAll()

case class StoredConfig(tapId: Long, config: Option[TapInstance])

case class StoredConfigs(configs: List[StoredConfig])

class ConfigStorageActor(implicit config: Config) extends ActorWithComposableBehavior {

  val storage = Storage(config)

  override def preStart(): Unit = {
    super.preStart()
    logger.info(s"Creating DB in ${config.getString("agent.storage.directory")}, provider $storage")
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case StoreDatasourceInstance(TapInstance(tapId, c, s)) =>
      logger.debug(s"Persisted config and state for flow $tapId")
      storage.store(tapId, c, s)
    case StoreDatasourceState(TapState(tapId, s)) =>
      logger.debug(s"Persisted state for flow $tapId")
      storage.storeState(tapId, s)
    case StoreDatasourceConfig(TapConfig(tapId, s)) =>
      logger.debug(s"Persisted config for flow $tapId")
      storage.storeConfig(tapId, s)
    case RetrieveConfigFor(tapId) =>
      sender() ! StoredConfig(tapId, storage.retrieve(tapId) map { case (c, s) => TapInstance(tapId, c, s)})
    case RetrieveConfigForAll() =>
      sender() ! StoredConfigs(storage.retrieveAll().map {
        case (fId, c, s) => StoredConfig(fId, Some(TapInstance(fId, c, s)))
      })
  }
}

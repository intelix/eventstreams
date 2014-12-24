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

package eventstreams.core.storage

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import eventstreams.core.actors.{ActorObjWithConfig, ActorWithComposableBehavior}
import play.api.libs.json.{JsValue, Json}

/**
 * Created by maks on 22/09/2014.
 */
object ConfigStorageActor extends ActorObjWithConfig {
  override val id = "cfgStorage"

  override def props(implicit config: Config) = Props(new ConfigStorageActor())
}

case class EntryConfigSnapshot(key: String, config: JsValue, state: Option[JsValue])

case class EntryStateConfig(key: String, state: Option[JsValue])

case class EntryPropsConfig(key: String, state: JsValue)

case class StoreSnapshot(config: EntryConfigSnapshot)

case class StoreProps(config: EntryPropsConfig)

case class StoreState(config: EntryStateConfig)

case class RetrieveConfigFor(key: String)

case class RemoveConfigFor(key: String)

case class RetrieveConfigForAllMatching(partialKey: String)

case class StoredConfig(key: String, config: Option[EntryConfigSnapshot])

case class StoredConfigs(configs: List[StoredConfig])

class ConfigStorageActor(implicit config: Config) extends ActorWithComposableBehavior {

  val storage = Storage(config)

  override def preStart(): Unit = {
    super.preStart()
//    logger.info(s"Creating DB in ${config.getString("ehub.storage.directory")}, provider $storage")
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case StoreSnapshot(EntryConfigSnapshot(key, c, s)) =>
      logger.debug(s"Persisted config and state for flow $key")
      storage.store(key, Json.stringify(c), s.map(Json.stringify))
    case StoreState(EntryStateConfig(key, s)) =>
      logger.debug(s"Persisted state for flow $key")
      storage.storeState(key, s.map(Json.stringify))
    case StoreProps(EntryPropsConfig(key, s)) =>
      logger.debug(s"Persisted config for flow $key")
      storage.storeConfig(key, Json.stringify(s))
    case RetrieveConfigFor(key) =>
      logger.debug(s"Retrieving config for $key")
      sender() ! StoredConfig(key, storage.retrieve(key) map {
        case (c, s) => EntryConfigSnapshot(key, Json.parse(c), s.map(Json.parse))
      })
    case RemoveConfigFor(key) =>
      logger.debug(s"Removing config entry $key")
      storage.remove(key)
    case RetrieveConfigForAllMatching(partialKey) =>
      logger.debug(s"Retrieving config for all matching $partialKey")
      sender() ! StoredConfigs(storage.retrieveAllMatching(partialKey).map {
        case (fId, c, s) => StoredConfig(fId, Some(EntryConfigSnapshot(fId, Json.parse(c), s.map(Json.parse))))
      })
  }
}

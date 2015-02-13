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

package eventstreams.core.storage

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{ActorObjWithConfig, ActorWithComposableBehavior, BaseActorSysevents}
import play.api.libs.json.{JsValue, Json}

trait ConfigStorageActorSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  override def componentId: String = "ConfigStorage"

  val PropsAndStateStored = 'PropsAndStateStored.info
  val StateStored = 'StateStored.info
  val PropsStored = 'PropsStored.info
  val RequestedSingleEntry = 'RequestedSingleEntry.info
  val RequestedAllMatchingEntries = 'RequestedAllMatchingEntries.info
  val RemovedEntry = 'RemovedEntry.info

}

object ConfigStorageActor extends ActorObjWithConfig with ConfigStorageActorSysevents {
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

class ConfigStorageActor(implicit config: Config)
  extends ActorWithComposableBehavior
  with ConfigStorageActorSysevents with WithSyseventPublisher {

  val storage = Storage(config)

  override def preStart(): Unit = {
    super.preStart()
  }

  override def commonBehavior: Actor.Receive = super.commonBehavior orElse {
    case StoreSnapshot(EntryConfigSnapshot(key, c, s)) =>
      storage.store(key, Json.stringify(c), s.map(Json.stringify))
      PropsAndStateStored >> ('Key -> key)

    case StoreState(EntryStateConfig(key, s)) =>
      storage.storeState(key, s.map(Json.stringify))
      StateStored >> ('Key -> key)

    case StoreProps(EntryPropsConfig(key, s)) =>
      storage.storeConfig(key, Json.stringify(s))
      PropsStored >> ('Key -> key)

    case RetrieveConfigFor(key) =>
      RequestedSingleEntry >> ('Key -> key)
      sender() ! StoredConfig(key, storage.retrieve(key) map {
        case (c, s) => EntryConfigSnapshot(key, Json.parse(c), s.map(Json.parse))
      })
      
    case RemoveConfigFor(key) =>
      RemovedEntry >> ('Key -> key)
      storage.remove(key)
      
    case RetrieveConfigForAllMatching(partialKey) =>
      RequestedAllMatchingEntries >> ('PartialKey -> partialKey)
      sender() ! StoredConfigs(storage.retrieveAllMatching(partialKey).map {
        case (fId, c, s) => StoredConfig(fId, Some(EntryConfigSnapshot(fId, Json.parse(c), s.map(Json.parse))))
      })
      
  }
}

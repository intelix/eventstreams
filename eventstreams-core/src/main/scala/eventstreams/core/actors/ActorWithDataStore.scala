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

package eventstreams.core.actors

import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.storage._
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._


trait ActorWithDataStoreEvents extends ComponentWithBaseEvents {

  val DataApplied = 'DataApplied.trace
  val DataSetReceived = 'DataSetReceived.trace
  val DataReceived = 'DataReceived.trace

}

trait ActorWithDataStore extends ActorWithComposableBehavior with ActorWithDataStoreEvents {
  _: WithEventPublisher =>

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  private val configStore = ConfigStorageActor.path

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    loadEntry()
    loadAllEntries()
    super.preStart()
  }

  def applyData(key: String, data: JsValue): Unit

  def storageKey: Option[String] = None

  def partialStorageKey: Option[String] = None

  def loadAllEntries() = partialStorageKey.foreach(configStore ! RetrieveConfigForAllMatching(_))

  def loadEntry() = storageKey.foreach(configStore ! RetrieveConfigFor(_))

  def removeData() = {
    storageKey.foreach(configStore ! RemoveConfigFor(_))
  }

  def storeData(data: JsValue) =
    storageKey.foreach { key => configStore ! StoreSnapshot(EntryConfigSnapshot(key, data, None))}


  private def handler: Receive = {
    case StoredConfigs(list) =>
      list.foreach(_.config.foreach { e =>
        DataSetReceived >> ('PartialKey -> partialStorageKey)
        applyData(e.key, e.config)
      })
    case StoredConfig(k, cfg) =>
      DataReceived >>()
      applyData(k, cfg.map(_.config) | Json.obj())
  }

}

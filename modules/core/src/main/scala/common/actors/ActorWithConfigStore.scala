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

package common.actors

import common.storage._
import play.api.libs.json.JsValue

trait ActorWithConfigStore extends ActorWithComposableBehavior {

  private val configStore = ConfigStorageActor.path

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    loadAllConfigs()
    super.preStart()
  }

  def storageKey: String

  def loadAllConfigs() = configStore ! RetrieveConfigForAllMatching(storageKey)
  def removeConfig() = configStore ! RemoveConfigFor(storageKey)
  def updateConfigSnapshot(config: JsValue, state: Option[JsValue]) =
    configStore ! StoreSnapshot(EntryConfigSnapshot(storageKey, config, state))
  def updateConfigProps(config: JsValue) =
    configStore ! StoreProps(EntryPropsConfig(storageKey, config))
  def updateConfigState(state: Option[JsValue]) =
    configStore ! StoreState(EntryStateConfig(storageKey, state))

  def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit

  private def handler: Receive = {
    case StoredConfigs(list) => list.foreach(_.config.foreach { e => applyConfig(e.key, e.config, e.state)})
    case StoredConfig(_, cfg) => cfg.foreach { e => applyConfig(e.key, e.config, e.state)}
  }

}

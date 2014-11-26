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
import common.{Fail, OK}
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

case class InitialConfig(config: JsValue, state: Option[JsValue])

trait ActorWithConfigStore extends ActorWithComposableBehavior {

  private val configStore = ConfigStorageActor.path
  var propsConfig: Option[JsValue] = None
  var stateConfig: Option[JsValue] = None

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    loadConfig()
    loadAllConfigs()
    super.preStart()
  }

  def beforeApplyConfig(): Unit = {}

  def afterApplyConfig(): Unit = {}

  def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit

  def storageKey: Option[String] = None

  def partialStorageKey: Option[String] = None

  def loadAllConfigs() = partialStorageKey.foreach(configStore ! RetrieveConfigForAllMatching(_))

  def loadConfig() = storageKey.foreach(configStore ! RetrieveConfigFor(_))

  def removeConfig() = {
    propsConfig = None
    stateConfig = None
    storageKey.foreach(configStore ! RemoveConfigFor(_))
  }

  def updateConfigSnapshot(props: JsValue, state: Option[JsValue]): \/[Fail, OK] = {
    storageKey.foreach { key => configStore ! StoreSnapshot(EntryConfigSnapshot(key, props, state))}
    cacheAndApplyConfig(props, state)
    OK().right
  }

  def updateConfigProps(props: JsValue): \/[Fail, OK] = {
    storageKey.foreach { key => configStore ! StoreProps(EntryPropsConfig(key, props))}
    cacheAndApplyConfig(props, stateConfig)
    OK().right
  }

  def updateConfigState(state: Option[JsValue]): \/[Fail, OK] = {
    storageKey.foreach { key => configStore ! StoreState(EntryStateConfig(key, state))}
    propsConfig.foreach(cacheAndApplyConfig(_, state))
    OK().right
  }

  def onInitialConfigApplied(): Unit = {}

  private def cacheAndApplyConfig(props: JsValue, maybeState: Option[JsValue]): Unit = {
    val isInitialConfig = propsConfig.isEmpty
    propsConfig = Some(props)
    stateConfig = maybeState
    storageKey.foreach { key =>
      beforeApplyConfig()
      applyConfig(key, props, maybeState)
      afterApplyConfig()
      logger.info(s"Applied configuration with key $key, initial: $isInitialConfig")
      if (isInitialConfig) onInitialConfigApplied()
    }
  }

  private def handler: Receive = {
    case StoredConfigs(list) =>
      beforeApplyConfig()
      list.foreach(_.config.foreach { e =>
        logger.debug(s"Received configuration set matching partial key $partialStorageKey")
        applyConfig(e.key, e.config, e.state)
      })
      afterApplyConfig()
    case StoredConfig(_, cfg) => cfg.foreach { e => cacheAndApplyConfig(e.config, e.state)}
    case InitialConfig(c, s) => propsConfig match {
      case None =>
        logger.info(s"Received initial configuration: $c state: $s")
        updateConfigSnapshot(c, s)
      case Some(_) =>
        logger.info(s"Initial config received but ignored - the actor already initialised with the config: $propsConfig and state $stateConfig")
    }
  }

}

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

import eventstreams.core.storage._
import eventstreams.core.{Fail, OK}
import play.api.libs.json.{Json, JsValue}

import scalaz.Scalaz._
import scalaz._

case class InitialConfig(config: JsValue, state: Option[JsValue])

trait ActorWithConfigStore extends ActorWithComposableBehavior {

  private val configStore = ConfigStorageActor.path
  var propsConfig: Option[JsValue] = None
  var stateConfig: Option[JsValue] = None
  var initialConfigApplied = false

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

  def updateAndApplyConfigSnapshot(props: JsValue, state: Option[JsValue]): \/[Fail, OK] = {
    storeConfigSnapshot(props, state)
    cacheAndApplyConfig(props, state)
    OK().right
  }

  def updateWithoutApplyConfigSnapshot(props: JsValue, state: Option[JsValue]): \/[Fail, OK] = {
    storeConfigSnapshot(props, state)
    cacheConfig(props, state)
    OK().right
  }

  def updateAndApplyConfigProps(props: JsValue): \/[Fail, OK] = {
    storeConfigProps(props)
    cacheAndApplyConfig(props, stateConfig)
    OK().right
  }

  def updateWithoutApplyConfigProps(props: JsValue): \/[Fail, OK] = {
    storeConfigProps(props)
    cacheConfig(props, stateConfig)
    OK().right
  }

  def updateAndApplyConfigState(state: Option[JsValue]): \/[Fail, OK] = {
    storeConfigState(state)
    propsConfig.foreach(cacheAndApplyConfig(_, state))
    OK().right
  }

  def updateWithoutApplyConfigState(state: Option[JsValue]): \/[Fail, OK] = {
    storeConfigState(state)
    propsConfig.foreach(cacheConfig(_, state))
    OK().right
  }

  def onInitialConfigApplied(): Unit = {}

  private def storeConfigSnapshot(props: JsValue, state: Option[JsValue]) =
    storageKey.foreach { key => configStore ! StoreSnapshot(EntryConfigSnapshot(key, props, state))}

  private def storeConfigProps(props: JsValue) =
    storageKey.foreach { key => configStore ! StoreProps(EntryPropsConfig(key, props))}

  private def storeConfigState(state: Option[JsValue]) =
    storageKey.foreach { key => configStore ! StoreState(EntryStateConfig(key, state))}

  private def cacheAndApplyConfig(props: JsValue, maybeState: Option[JsValue]): Unit = {
    val isInitialConfig = propsConfig.isEmpty
    if (!isInitialConfig && propsConfig.get.equals(props) && stateConfig.equals(maybeState)) {
      logger.debug(s"Configuration and state have not changed - update ignored")
    } else {
      cacheConfig(props, maybeState)
      storageKey.foreach { key =>
        beforeApplyConfig()
        applyConfig(key, props, maybeState)
        afterApplyConfig()
        logger.info(s"Applied configuration with key $key, initial: $isInitialConfig")
        if (isInitialConfig) {
          initialConfigApplied = true
          onInitialConfigApplied()
        }
      }
    }
  }

  private def cacheConfig(props: JsValue, maybeState: Option[JsValue]): Unit = {
    propsConfig = Some(props)
    stateConfig = maybeState
  }

  private def handler: Receive = {
    case StoredConfigs(list) =>
      beforeApplyConfig()
      list.foreach(_.config.foreach { e =>
        logger.debug(s"Received configuration set matching partial key $partialStorageKey")
        applyConfig(e.key, e.config, e.state)
      })
      afterApplyConfig()
    case StoredConfig(k, cfg) =>
      logger.debug(s"Received stored config")
      val config = cfg | EntryConfigSnapshot(k, Json.obj(), None)
      cacheAndApplyConfig(config.config, config.state)
    case InitialConfig(c, s) => propsConfig match {
      case None =>
        logger.info(s"Received initial configuration: $c state: $s")
        updateAndApplyConfigSnapshot(c, s)
      case Some(_) =>
        logger.info(s"Initial config received but ignored - the actor already initialised with the config: $propsConfig and state $stateConfig")
    }
  }

}

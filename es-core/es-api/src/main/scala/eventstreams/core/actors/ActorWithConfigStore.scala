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

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.storage._
import eventstreams.{Fail, NowProvider, OK}
import play.api.libs.json.{JsValue, Json}

import scalaz.Scalaz._
import scalaz._


trait ActorWithConfigStoreSysevents extends ComponentWithBaseSysevents {

  val ConfigurationUpdateIgnored = 'ConfigurationUpdateIgnored.trace
  val ConfigurationApplied = 'ConfigurationApplied.trace
  val ConfigurationSetReceived = 'ConfigurationSetReceived.trace
  val ConfigurationReceived = 'ConfigurationReceived.trace

  val InitialConfigurationApplied = 'InitialConfigurationApplied.info
  val InitialConfigurationIgnored = 'InitialConfigurationIgnored.info

}

case class InitialConfig(config: JsValue, state: Option[JsValue])

trait ActorWithConfigStore extends ActorWithComposableBehavior with ActorWithConfigStoreSysevents with ActorWithTicks with NowProvider {
  _: WithSyseventPublisher =>

  private val configStore = ConfigStorageActor.path
  var propsConfig: Option[JsValue] = None
  var stateConfig: Option[JsValue] = None
  var initialConfigApplied = false

  private var pendingStorageProps = false
  private var pendingStorageState = false

  private var lastPersistenceTimestamp: Option[Long] = None

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


  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    storePending()
    super.postStop()
  }


  @throws[Exception](classOf[Exception]) override
  def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    storePending()
    super.preRestart(reason, message)
  }

  def removeConfig() = {
    propsConfig = None
    stateConfig = None
    storageKey.foreach(configStore ! RemoveConfigFor(_))
  }

  def updateAndApplyConfigSnapshot(props: JsValue, state: Option[JsValue]): \/[Fail, OK] = {
    pendingStorageProps = true
    pendingStorageState = true
    cacheAndApplyConfig(props, state)
    OK().right
  }

  def updateWithoutApplyConfigSnapshot(props: JsValue, state: Option[JsValue]): \/[Fail, OK] = {
    pendingStorageProps = true
    pendingStorageState = true
    cacheConfig(props, state)
    OK().right
  }

  def updateAndApplyConfigProps(props: JsValue): \/[Fail, OK] = {
    pendingStorageProps = true
    cacheAndApplyConfig(props, stateConfig)
    OK().right
  }

  def updateWithoutApplyConfigProps(props: JsValue): \/[Fail, OK] = {
    pendingStorageProps = true
    storeConfigProps(props)
    cacheConfig(props, stateConfig)
    OK().right
  }

  def updateAndApplyConfigState(state: Option[JsValue]): \/[Fail, OK] = {
    pendingStorageState = true
    propsConfig.foreach(cacheAndApplyConfig(_, state))
    OK().right
  }

  def updateWithoutApplyConfigState(state: Option[JsValue]): \/[Fail, OK] = {
    pendingStorageState = true
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

  private def storePending() = {
    if (pendingStorageProps && pendingStorageState)
      storeConfigSnapshot(propsConfig | Json.obj(), stateConfig)
    else if (pendingStorageState)
      storeConfigState(stateConfig)
    else if (pendingStorageProps)
      storeConfigProps(propsConfig | Json.obj())
    pendingStorageProps = false
    pendingStorageState = false
  }

  private def cacheAndApplyConfig(props: JsValue, maybeState: Option[JsValue]): Unit = {
    val isInitialConfig = propsConfig.isEmpty
    if (!isInitialConfig && propsConfig.get.equals(props) && stateConfig.equals(maybeState)) {
      ConfigurationUpdateIgnored >> ('Reason ->  "Configuration and state have not changed")
    } else {
      cacheConfig(props, maybeState)
      storageKey.foreach { key =>
        beforeApplyConfig()
        applyConfig(key, props, maybeState)
        afterApplyConfig()
        ConfigurationApplied >> ('Key -> key, 'Initial -> isInitialConfig)
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
        ConfigurationSetReceived >> ('PartialKey -> partialStorageKey)
        applyConfig(e.key, e.config, e.state)
      })
      afterApplyConfig()
    case StoredConfig(k, cfg) =>
      cfg.foreach { config =>
        ConfigurationReceived >> ()
        cacheAndApplyConfig(config.config, config.state)
      }
    case InitialConfig(c, s) => propsConfig match {
      case None =>
        updateAndApplyConfigSnapshot(c, s)
        InitialConfigurationApplied >> ('Props -> c, 'State -> s)
      case Some(_) =>
        InitialConfigurationIgnored >> ('Reason -> "actor already initialised", 'Props -> c, 'State -> s)
    }
  }

  override def internalProcessTick(): Unit = {
    lastPersistenceTimestamp = lastPersistenceTimestamp match {
      case Some(t) if now - t < 3000 => lastPersistenceTimestamp
      case _ =>
        storePending()
        Some(now)
    }
    super.internalProcessTick()
  }
}

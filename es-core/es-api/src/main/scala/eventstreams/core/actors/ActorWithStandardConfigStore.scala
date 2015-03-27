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
import eventstreams.{WithID, Fail, NowProvider, OK}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz._


trait ActorWithStandardConfigStore
  extends WithID[String]
  with ActorWithComposableBehavior
  with ActorWithTicks {

  _: WithSyseventPublisher =>

  private val configStore = ConfigStorageActor.path

  val initialConfig: ModelConfigSnapshot

  var propsConfig: JsValue = initialConfig.config
  var metaConfig: JsValue = initialConfig.meta
  var stateConfig: Option[JsValue] = initialConfig.state

  private var pendingStorageProps = false
  private var pendingStorageMeta = false
  private var pendingStorageState = false

  private var lastPersistenceTimestamp: Option[Long] = None

  final def storageKey: Option[String] = Some(entityId)

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
    propsConfig = Json.obj()
    metaConfig = Json.obj()
    stateConfig = None
    storageKey.foreach(configStore ! RemoveConfigFor(_))
  }

  def onPropsConfigChanged() {}
  def onStateConfigChanged() {}

  def updateConfigSnapshot(props: JsValue, state: Option[JsValue]): Unit = {
    pendingStorageProps = true
    pendingStorageState = true
    cacheConfig(props, state)
    OK()
  }

  def updateConfigMeta(values: Tuple2[JsPath, JsValue]*): Unit = updateConfigMeta(metaConfig.set(values:_*))

  def updateConfigMeta(meta: JsValue): Unit = {
    pendingStorageMeta = true
    metaConfig = meta
    OK()
  }

  def updateConfigProps(props: JsValue): Unit = {
    pendingStorageProps = true
    cacheConfig(props, stateConfig)
    OK()
  }

  def updateConfigState(state: Option[JsValue]): Unit = {
    pendingStorageState = true
    cacheConfig(propsConfig, state)
    OK()
  }

  private def storeConfigSnapshot(props: JsValue, state: Option[JsValue]) =
    storageKey.foreach { key => configStore ! StoreSnapshot(EntryConfigSnapshot(key, props, metaConfig, state))}

  private def storeConfigProps(props: JsValue) =
    storageKey.foreach { key => configStore ! StoreProps(EntryPropsConfig(key, props))}

  private def storeConfigMeta(meta: JsValue) =
    storageKey.foreach { key => configStore ! StoreMeta(EntryMetaConfig(key, meta)) }

  private def storeConfigState(state: Option[JsValue]) =
    storageKey.foreach { key => configStore ! StoreState(EntryStateConfig(key, state))}

  private def storePending() = {
    if (pendingStorageProps && pendingStorageState) {
      storeConfigSnapshot(propsConfig , stateConfig)
      storeConfigMeta(metaConfig)
      pendingStorageProps = false
      pendingStorageState = false
      pendingStorageMeta = false
    } else if (pendingStorageState) {
      storeConfigState(stateConfig)
      pendingStorageState = false
    } else if (pendingStorageProps) {
      storeConfigProps(propsConfig)
      pendingStorageProps = false
    } else if (pendingStorageMeta) {
      storeConfigMeta(metaConfig)
      pendingStorageMeta = false
    }
  }

  private def cacheConfig(props: JsValue, maybeState: Option[JsValue]): Unit = {
    propsConfig = props
    stateConfig = maybeState
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

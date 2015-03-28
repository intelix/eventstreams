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
import play.api.libs.json._


trait ActorWithConfigAutoLoadSysevents extends ComponentWithBaseSysevents {

  val ConfigurationSetReceived = 'ConfigurationSetReceived.trace

}

trait ActorWithConfigAutoLoad
  extends ActorWithComposableBehavior
  with ActorWithConfigAutoLoadSysevents  {

  _: WithSyseventPublisher =>

  private val configStore = ConfigStorageActor.path

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    loadAllConfigs()
    super.preStart()
  }

  def applyConfig(key: String, props: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit

  def partialStorageKey: Option[String] = None

  def loadAllConfigs() = {
    println("!>>>> Requesting configs for " + partialStorageKey)
    partialStorageKey.foreach(configStore ! RetrieveConfigForAllMatching(_))
  }

  def storeConfigFor(key: String, c: ModelConfigSnapshot) = configStore ! StoreSnapshot(EntryConfigSnapshot(key, c.config, c.meta, c.state))

  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    super.postStop()
  }


  @throws[Exception](classOf[Exception]) override
  def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
  }


  def beforeApplyConfig() = {}
  def afterApplyConfig() = {}

  private def handler: Receive = {
    case StoredConfigs(list) =>
      beforeApplyConfig()
      list.foreach(_.config.foreach { e =>
        ConfigurationSetReceived >> ('PartialKey -> partialStorageKey)
        applyConfig(e.key, e.config, e.meta, e.state)
      })
      afterApplyConfig()
  }

}

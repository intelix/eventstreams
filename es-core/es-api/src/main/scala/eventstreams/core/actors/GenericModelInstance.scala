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

import akka.actor._
import eventstreams.Model

trait GenericModelInstance
  extends ActorWithStandardConfigStore {

  def modelEntryInfo: Model

  override def preStart(): Unit = {
    super.preStart()
    context.parent ! InstanceAvailable(modelEntryInfo)
  }

  def restartModel() = {
    context.parent ! RestartRequest(entityId, ModelConfigSnapshot(propsConfig, metaConfig, stateConfig))
    context.stop(self)
  }

  def destroyModel() = {
    removeConfig()
    context.stop(self)
  }

}

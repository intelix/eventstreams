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

import eventstreams._
import play.api.libs.json._

trait RouteeModelManager[T <: Model]
  extends GenericModelManager[T]
  with ActorWithComposableBehavior
  with ActorWithConfigAutoLoad
  with RouteeActor {

  def list: Option[JsValue]

  def configSchema: Option[JsValue] = None

  def publishList() = T_LIST !! list

  def publishConfigTpl(): Unit = T_CONFIGTPL !! configSchema


  override def onModelListChange(): Unit = {
    publishList()
    super.onModelListChange()
  }

  override def onSubscribe: SubscribeHandler = super.onSubscribe orElse {
    case T_LIST => publishList()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def onCommand(maybeData: Option[JsValue]): CommandHandler = super.onCommand(maybeData) orElse {
    case T_ADD => for (
      x <- createModelInstance(None, maybeData, None, None);
      (key, _, c) = x
    ) yield {
        storeConfigFor(key, c)
        Successful(onSuccessfulAdd())
      }
  }


  def onSuccessfulAdd() = Some("Successfully created")


}

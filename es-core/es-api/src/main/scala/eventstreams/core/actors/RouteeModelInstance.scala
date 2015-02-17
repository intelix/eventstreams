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
import eventstreams.{Fail, OK}
import play.api.libs.json._

import scalaz.Scalaz._


trait RouteeModelInstance
  extends ActorWithConfigStore
  with RouteeActor {

  def publishInfo() = T_INFO !! info
  def publishStats() = T_STATS !! stats
  def publishProps() = T_PROPS !! propsConfig

  override def afterApplyConfig(): Unit = {
    publishInfo()
    publishStats()
    publishProps()
  }

  def info: Option[JsValue]
  def stats: Option[JsValue] = None
  def publishAvailable(): Unit

  override def onInitialConfigApplied(): Unit = publishAvailable()


  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_REMOVE =>
      removeConfig()
      self ! PoisonPill
      OK().right
    case T_UPDATE_PROPS =>
      for (
        data <- maybeData \/> Fail("Invalid request");
        result <- updateAndApplyConfigProps(data)
      ) yield {
        publishAvailable()
        result
      }
  }

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_INFO => publishInfo()
    case T_PROPS => publishProps()
    case T_STATS => publishStats()
  }

}

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
import eventstreams.{Fail, NowProvider, OK, _}
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._

trait RouteeModelManager[T <: Model]
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider {

  def configSchema: Option[JsValue] = None
  def list: Option[JsValue]
  def startModelActor(key: String): ActorRef

  var entries: List[T] = List()

  override def partialStorageKey: Option[String] = Some(key.key + "/")

  def publishList() = T_LIST !! list
  def publishConfigTpl(): Unit = T_CONFIGTPL !! configSchema

  override def onSubscribe : SubscribeHandler = super.onSubscribe orElse {
    case T_LIST => publishList()
    case T_CONFIGTPL => publishConfigTpl()
  }

  override def onCommand(maybeData: Option[JsValue]) : CommandHandler = super.onCommand(maybeData) orElse {
    case T_ADD => createModelInstance(None, maybeData, None)
  }

  override def onTerminated(ref: ActorRef): Unit = {
    entries = entries.filter(_.ref != ref)
    publishList()
    super.onTerminated(ref)
  }

  def addEntry(e: T) = {
    entries = (entries.filter(!_.equals(e)) :+ e).sortBy(_.sortBy)
    publishList()
  }
  
  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = createModelInstance(Some(key), Some(props), maybeState)

  private def createModelInstance(k: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]) =
    for (
      data <- maybeData \/> Fail("Invalid payload")
    ) yield {
      val entryKey = k | (key / shortUUID).key
      var json = data
      if (k.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = startModelActor(entryKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      onSuccessfulAdd()
    }


  def onSuccessfulAdd() = OK("Successfully created")


}

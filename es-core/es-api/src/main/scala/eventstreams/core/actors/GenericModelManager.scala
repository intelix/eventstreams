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

import _root_.core.sysevents.SyseventOps.symbolToSyseventOps
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import eventstreams.Tools.optionsHelper
import eventstreams._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scalaz.Scalaz._
import scalaz.\/

case class RestartRequest(entityId: String, config: ModelConfigSnapshot)
case class InstanceAvailable(e: Model)

trait GenericModelManagerSysevents
  extends ComponentWithBaseSysevents
  with BaseActorSysevents
  with ReconnectingActorSysevents {

  val EntityInstanceAvailable = 'EntityInstanceAvailable.info

}


trait GenericModelManager[T <: Model]
  extends WithID[String]
  with ActorWithComposableBehavior
  with ActorWithTicks
  with ActorWithConfigAutoLoad
  with GenericModelManagerSysevents {

  type CreatedModelInfo = (String, ActorRef, ModelConfigSnapshot)

  var entries: List[T] = List()
  var restartPending: Map[ActorRef, RestartRequest] = Map()

  private var pendingListPublish: Boolean = false

  def startModelActor(entityId: String, config: ModelConfigSnapshot): ActorRef

  def onModelListChange(): Unit = {}

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  final override def partialStorageKey: Option[String] = Some(entityId + "/")

  override def internalProcessTick(): Unit = {
    super.internalProcessTick()
    if (pendingListPublish) {
      onModelListChange()
      pendingListPublish = false
    }

  }

  override def onTerminated(ref: ActorRef): Unit = {
    entries = entries.filter(_.ref != ref)
    pendingListPublish = true
    restartPending.get(ref) foreach {
      case RestartRequest(eId, cfg) =>
        restartPending -= ref
        createModelInstance(Some(eId), Some(cfg.config), Some(cfg.meta), cfg.state)
    }
    super.onTerminated(ref)
  }

  def addEntry(e: T) = {
    entries = (entries.filter(!_.equals(e)) :+ e).sortBy(_.sortBy)
    EntityInstanceAvailable >> ('Key -> e.id, 'Actor -> sender())
    pendingListPublish = true
  }

  final override def applyConfig(key: String, props: JsValue, meta: JsValue, maybeState: Option[JsValue]): Unit =
    createModelInstance(Some(key), Some(props), Some(meta), maybeState)

  final def createModelInstance(k: Option[String], maybeData: Option[JsValue], meta: Option[JsValue], maybeState: Option[JsValue]): \/[Fail, CreatedModelInfo] =
    for (
      data <- maybeData orFail "Invalid payload"
    ) yield {
      val eId = k | (entityId + "/" + shortUUID)
      var json = meta | Json.obj()
      if (k.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val c = ModelConfigSnapshot(data, json, maybeState)
      val actor = startModelActor(eId, c)
      context.watch(actor)
      (eId, actor, c)
    }

  private def handler: Receive = {
    case rr: RestartRequest => restartPending += (sender() -> rr)
    case InstanceAvailable(x) => addEntry(x.asInstanceOf[T])
  }


}

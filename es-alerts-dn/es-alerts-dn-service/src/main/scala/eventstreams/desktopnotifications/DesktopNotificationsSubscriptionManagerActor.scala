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

package eventstreams.desktopnotifications

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor._
import com.typesafe.config.Config
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.collection.mutable
import scala.io.Source
import scalaz.Scalaz._
import scalaz.\/

trait DesktopNotificationsSubscriptionManagerSysevents extends ComponentWithBaseSysevents {
  override def componentId: String = "DesktopNotificationsSubscriptionManager"
}

object DesktopNotificationsSubscriptionManagerActor extends ActorObjWithConfig {
  def id = "desktopnotifications"

  def props(implicit config: Config) = Props(new DesktopNotificationsSubscriptionManagerActor(config))
}

case class DesktopNotificationsSubscriptionAvailable(id: ComponentKey)

class DesktopNotificationsSubscriptionManagerActor(sysconfig: Config)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeActor
  with NowProvider
  with DesktopNotificationsSubscriptionManagerSysevents
  with WithSyseventPublisher {

  val configSchema = Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.desktopnotifications.main-schema"))).mkString)


  override val key = ComponentKey("desktopnotifications")

  var entries = mutable.Map[ComponentKey, ActorRef]()

  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  override def partialStorageKey = Some("desktopnotification/")

  override def applyConfig(key: String, props: JsValue, maybeState: Option[JsValue]): Unit = startActor(Some(key), Some(props), maybeState)

  def publishList() = T_LIST !! list

  def list = Some(Json.toJson(entries.keys.map { x => Json.obj("ckey" -> x.key)}.toArray))

  def publishConfigTpl(): Unit = T_CONFIGTPL !! Some(configSchema)

  override def processTopicSubscribe(ref: ActorRef, topic: TopicKey) = topic match {
    case T_LIST => publishList()
    case T_CONFIGTPL => publishConfigTpl()
  }


  override def processTopicCommand(topic: TopicKey, replyToSubj: Option[Any], maybeData: Option[JsValue]) = topic match {
    case T_ADD => startActor(None, maybeData, None)
  }


  override def onTerminated(ref: ActorRef): Unit = {
    entries.collect { case (k, v) if v == ref => k} foreach entries.remove
    publishList()

    super.onTerminated(ref)
  }

  def handler: Receive = {
    case m: Acknowledgeable[_] =>
      sender() ! AcknowledgeAsProcessed(m.id)
      entries.values.foreach(_ ! m.msg)
    case DesktopNotificationsSubscriptionAvailable(route) =>
      entries += route -> sender()
      publishList()
  }

  private def startActor(key: Option[String], maybeData: Option[JsValue], maybeState: Option[JsValue]): \/[Fail, OK] =
    for (
      data <- maybeData \/> Fail("Invalid payload", Some("Invalid configuration"))
    ) yield {
      val entryKey = key | "desktopnotification/" + shortUUID
      var json = data
      if (key.isEmpty) json = json.set(__ \ 'created -> JsNumber(now))
      val actor = DesktopNotificationsSubscriptionActor.start(entryKey)
      context.watch(actor)
      actor ! InitialConfig(json, maybeState)
      OK("Desktop notification subscription successfully created")
    }

}

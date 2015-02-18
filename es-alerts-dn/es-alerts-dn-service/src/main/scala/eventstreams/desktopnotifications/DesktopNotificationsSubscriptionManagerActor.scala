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
import akka.cluster.Cluster
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
  override def componentId: String = "DesktopNotifications.Manager"
}

case class DesktopNotificationsSubscriptionAvailable(id: ComponentKey, ref: ActorRef, name: String) extends Model

object DesktopNotificationsSubscriptionManagerActor extends DesktopNotificationsSubscriptionManagerSysevents {
  val id = "desktopnotifications"
}

class DesktopNotificationsSubscriptionManagerActor(sysconfig: Config, cluster: Cluster)
  extends ActorWithComposableBehavior
  with ActorWithConfigStore
  with RouteeModelManager[DesktopNotificationsSubscriptionAvailable]
  with NowProvider
  with DesktopNotificationsSubscriptionManagerSysevents
  with WithSyseventPublisher {

  override val configSchema = Some(Json.parse(
    Source.fromInputStream(
      getClass.getResourceAsStream(
        sysconfig.getString("eventstreams.desktopnotifications.signalsub-schema"))).mkString))

  val id = DesktopNotificationsSubscriptionManagerActor.id

  override val key = ComponentKey(id)


  override def commonBehavior: Actor.Receive = handler orElse super.commonBehavior

  def list = Some(Json.toJson(entries.map { x => Json.obj("ckey" -> x.id.key)}.toArray))

  def forward(m: Any) = entries.foreach(_.ref ! m)

  private def handler: Receive = {
    case m: Acknowledgeable[_] =>
      sender() ! AcknowledgeAsProcessed(m.id)

      m.msg match {
        case x: Batch[_] => x.entries.foreach(forward)
        case x => forward(x)
      }

    case m: DesktopNotificationsSubscriptionAvailable => addEntry(m)
  }

  override def startModelActor(key: String): ActorRef = DesktopNotificationsSubscriptionActor.start(key)
}

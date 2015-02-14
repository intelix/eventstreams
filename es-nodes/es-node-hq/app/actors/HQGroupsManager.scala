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
package actors

import _root_.core.sysevents.WithSyseventPublisher
import _root_.core.sysevents.ref.ComponentWithBaseSysevents
import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import com.typesafe.config.Config
import eventstreams.JSONTools.configHelper
import eventstreams._
import eventstreams.core.actors.{ActorObjWithCluster, ActorWithComposableBehavior, RouteeActor}
import eventstreams.core.components.cluster.ClusterManagerActor
import eventstreams.core.components.routing.MessageRouterActor
import net.ceedubs.ficus.Ficus._
import play.api.libs.json.{JsArray, JsValue, Json}
import play.twirl.api.TemplateMagic.javaCollectionToScala

import scalaz.Scalaz._

trait HQGroupsManagerSysevents
  extends ComponentWithBaseSysevents {

  override def componentId: String = "HQ.GroupsManager"
}

object HQGroupsManager extends ActorObjWithCluster with HQGroupsManagerSysevents with WithSyseventPublisher {
  def id = "unsecured_hqgroups"

  def props(implicit cluster: Cluster, config: Config) = Props(new HQGroupsManagerActor(config))
}

class HQGroupsManagerActor(config: Config)
  extends ActorWithComposableBehavior
  with HQGroupsManagerSysevents
  with RouteeActor
  with WithSyseventPublisher {

  def key = ComponentKey(HQGroupsManager.id)

  val hqConfig = config.as[Config]("eventstreams.hq")

  var availableServices: Option[Set[String]] = None
  var groups: Option[JsValue] = None

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def preStart(): Unit = {
    super.preStart()
    MessageRouterActor.path ! Subscribe(self, LocalSubj(ComponentKey(ClusterManagerActor.id), TopicKey("nodes")))
  }


  override def processTopicSubscribe(sourceRef: ActorRef, topic: TopicKey): Unit = topic match {
    case T_LIST => T_LIST !! groups
  }

  def handleUpdate(data: String) = {
    availableServices = Json.parse(data).asOpt[JsArray].map { json =>
      json.value.flatMap {
        _ ##> 'roles | Seq()
      }.map {
        _.asOpt[String] | ""
      }.filter(_ != "").toSet
    }
    groups = availableServices.map { as =>
      val groupsSet = hqConfig.as[Set[String]]("groups")
      val servicesConf = hqConfig.as[Config]("modules")
      val serviceIDsList = servicesConf
        .entrySet()
        .map(_.getKey.split('.').headOption)
        .collect { case Some(x) if x != "" && as.contains(x) => x }
        .toSet

      val serviceObjArray =  serviceIDsList.collect {
        case nextServiceId if groupsSet.contains(servicesConf.as[String](nextServiceId + ".group")) =>
          (
            servicesConf.as[String](nextServiceId + ".name"),
            servicesConf.as[String](nextServiceId + ".group"),
            servicesConf.as[Option[Int]](nextServiceId + ".order") | 9999,
            nextServiceId
            )
      }.toArray.sortWith { (t1,t2) =>
        if (t1._3 == t2._3) t1._1 < t2._1 else t1._3 < t2._3
      }.map {
        case (name, group, _, id) => Json.obj(
          "id" -> id,
          "name" -> name,
          "group" -> group
        )
      }

      val allGroups = serviceIDsList.map { id => servicesConf.as[String](id + ".group") }.toSet
      val filteredGroupsSet = groupsSet.filter(allGroups.contains).toSet

      Json.obj(
        "groups" -> filteredGroupsSet,
        "services" -> Json.toJson(serviceObjArray)
      )
    }
    T_LIST !! groups
  }

  private def handler: Receive = {
    case Update(_, data, _) => handleUpdate(data)
  }
}

/*
 * Copyright 2014 Intelix Pty Ltd
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

package hq.gates

import java.util.Calendar

import akka.actor.{ActorRef, Props}
import akka.agent.Agent
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.source.StringDocumentSource
import com.typesafe.config.Config
import common.JsonFrame
import common.actors.{ActorObjWithConfig, ActorWithComposableBehavior}
import hq.flows.core.{DeliveryFailed, DeliverySuccessful}
import org.elasticsearch.common.settings.ImmutableSettings
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.Map
import scala.util.{Failure, Success}

object RetentionManagerActor extends ActorObjWithConfig {
  override def props(implicit config: Config): Props = Props(new RetentionManagerActor(config))

  override def id: String = "storage"
}

case class ScheduleStorage(ref: ActorRef, correlationId: Long, key: String, id: String, v: JsValue)

case class MessageStored(correlationId: Long)

class RetentionManagerActor(config: Config) extends ActorWithComposableBehavior {

  implicit val ec = context.dispatcher


  private val host = config.getString("ehub.gates.retention.elastic.host")
  private val cluster = config.getString("ehub.gates.retention.elastic.cluster")
  private val port = config.getInt("ehub.gates.retention.elastic.port")
  private val indextpl = config.getString("ehub.gates.retention.elastic.index")
  private val cal = Calendar.getInstance()

  private val settings = ImmutableSettings.settingsBuilder().put("cluster.name", cluster).build()
  private val queue = collection.mutable.Queue[JsonFrame]()
  private val clientAgent = Agent(Some(ElasticClient.remote(settings, (host, port))))

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def buildIndex(key: String) = Map(
    "key" -> key, "yyyy" -> cal.get(Calendar.YEAR), "mm" -> cal.get(Calendar.MONTH), "dd" -> cal.get(Calendar.DAY_OF_MONTH)
  ).foldLeft(indextpl) {
    case (k, (m, v)) => k.replaceAll("%" + m, v.toString)
  }

  def enqueue(m: ScheduleStorage) = {
    clientAgent.get().foreach { esclient =>
      val targetIndex = buildIndex(m.key)

      esclient.execute {
        logger.debug(s"Delivering -> $targetIndex : $m")
          index into targetIndex id m.id doc StringDocumentSource(Json.stringify(m.v))
      } onComplete {
        case Success(_) => m.ref ! MessageStored(m.correlationId)
        case Failure(fail) => logger.debug(s"Failed to deliver ${m.correlationId}", fail)
      }
    }
  }

  private def handler: Receive = {
    case m: ScheduleStorage => enqueue(m)
  }
}


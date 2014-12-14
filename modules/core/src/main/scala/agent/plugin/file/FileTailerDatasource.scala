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

package agent.plugin.file

import java.nio.charset.Charset

import agent.flavors.files._
import akka.actor.Props
import com.typesafe.scalalogging.StrictLogging
import common.{Fail, Utils}
import hq.flows.core.BuilderFromConfig
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

class FileTailerDatasource extends BuilderFromConfig[Props] with StrictLogging {
  override def configId: String = "file"

  def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, Props] = {
    logger.debug(s"Building FileMonitorActorPublisher from $props")
    implicit val charset = Charset.forName("UTF-8")
    implicit val fileIndexing = new FileIndexer
    FileMonitorActorPublisher.props(id|Utils.generateShortUUID, props, maybeState).right
  }
}

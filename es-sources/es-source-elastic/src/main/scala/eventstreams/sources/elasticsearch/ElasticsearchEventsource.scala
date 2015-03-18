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

package eventstreams.sources.elasticsearch

import akka.actor.Props
import com.typesafe.scalalogging.StrictLogging
import eventstreams.Tools.{optionsHelper, configHelper}
import eventstreams.{BuilderFromConfig, Fail}
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

class ElasticsearchEventsource extends BuilderFromConfig[Props] with StrictLogging {
  override def configId: String = "elasticsearch"

  def build(config: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, Props] =
    for (
      streamSeed <- id orFail s"streamSeed must be provided";
      streamKey <- config ~> 'streamKey orFail s"streamKey must be provided";
      props <- config #> 'source orFail s"Invalid configuration"
    ) yield ElasticsearchPublisher.props(streamKey, streamSeed, props, maybeState)

}

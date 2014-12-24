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

package eventstreams.ds.plugins.file

import akka.util.ByteString
import eventstreams.core.agent.core.Cursor
import play.api.libs.json.JsValue

import scala.concurrent.duration.DurationDouble

case class DataChunk(data: Option[ByteString], meta: Option[JsValue], cursor: Cursor, hasMore: Boolean)


trait ResourcePullingProxy {

  def pullRetryInterval = 3.second

  def next(c: Option[Cursor]): Option[DataChunk]

  def cancelResource()
}

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

package agent.flavors.files

import scala.concurrent.duration.DurationDouble

case class DataChunk[T, C <: Cursor](data: Option[T], cursor: C, hasMore: Boolean)


trait ResourcePullingProxy[T, C <: Cursor] {

  def pullRetryInterval = 3.second

  def next(c: Option[C]): Option[DataChunk[T, C]]

  def cancelResource()
}

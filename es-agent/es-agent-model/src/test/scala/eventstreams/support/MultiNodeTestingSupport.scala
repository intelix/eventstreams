package eventstreams.support

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

import com.typesafe.config.Config
import core.events.support.EventAssertions
import org.scalatest.Suite

trait MultiNodeTestingSupport extends EventAssertions with MultiActorSystemTestContext {
  _: Suite with ActorSystemManagement =>


  override def configs: Map[String, Config] = Map()

  def withSystem[T](prefix: String, idx: Int)(f: ActorSystemWrapper => T): T = withSystem[T](prefix + idx)(f)


}

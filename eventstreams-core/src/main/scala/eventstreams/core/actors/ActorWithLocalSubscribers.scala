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

import eventstreams.core.messages.{LocalSubj, RemoteAddrSubj}

trait ActorWithLocalSubscribers
  extends ActorWithSubscribers[LocalSubj] {

  override def convertSubject(subj: Any): Option[LocalSubj] = subj match {
    case local: LocalSubj => Some(local)
    case remote: RemoteAddrSubj => Some(remote.localSubj)
    case _ => None
  }

  override def subjectMatch(subj: LocalSubj, otherSubj: LocalSubj): Boolean = subj == otherSubj

}

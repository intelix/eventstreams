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

import eventstreams.{LocalSubj, RemoteAddrSubj}

trait ActorWithRemoteSubscribers
  extends ActorWithSubscribers[RemoteAddrSubj]
  with ActorWithClusterAwareness {

  override def convertSubject(subj: Any): Option[RemoteAddrSubj] = subj match {
    case local: LocalSubj => Some(RemoteAddrSubj(myAddress, local))
    case remote: RemoteAddrSubj => Some(remote)
    case _ => None
  }

  override def subjectMatch(subj: RemoteAddrSubj, otherSubj: RemoteAddrSubj): Boolean =
    subj.localSubj == otherSubj.localSubj && (aliasToFullAddress(subj.address) match {
      case Some(addr) => aliasToFullAddress(otherSubj.address) match {
        case Some(x) => x == addr
        case _ => false
      }
      case _ => false
    })

}

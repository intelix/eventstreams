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

package eventstreams.engine.signals

case class Signal(signalId: String,
                  sequenceId: Long,
                  ts: Long,
                  eventId: String,
                  level: SignalLevel,
                  signalClass: String,
                  signalSubclass: Option[String],
                  conflationKey: Option[String],
                  correlationId: Option[String],
                  transactionDemarcation: Option[String],
                  transactionStatus: Option[String],
                  title: Option[String],
                  body: Option[String],
                  icon: Option[String],
                  expiryTs: Option[Long])

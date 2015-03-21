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

package eventstreams.gauges

import akka.actor.Props
import eventstreams.signals._


object GaugeActor {

  def apply(t: SigMetricType, id: String, signalKey: SignalKey) = t match {
    case SigMetricTypeGauge() => Props(new GaugeTypeGaugeActor(id, signalKey))
    case SigMetricTypeTiming() => Props(new TimingTypeGaugeActor(id, signalKey))
    case SigMetricTypeOccurrence() => Props(new OccurrenceTypeGaugeActor(id, signalKey))
    case SigMetricTypeState() => Props(new StateTypeGaugeActor(id, signalKey))
  }

}

class GaugeTypeGaugeActor(val id: String, val signalKey: SignalKey) extends BaseGaugeActor with GaugeMetricAccounting
class TimingTypeGaugeActor(val id: String, val signalKey: SignalKey) extends BaseGaugeActor with TimingMetricAccounting
class OccurrenceTypeGaugeActor(val id: String, val signalKey: SignalKey) extends  BaseGaugeActor with OccurrenceMetricAccounting
class StateTypeGaugeActor(val id: String, val signalKey: SignalKey) extends BaseGaugeActor with StateMetricAccounting


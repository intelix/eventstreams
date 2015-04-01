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

package eventstreams

import eventstreams.gauges.{GaugesManagerConstants, LevelUpdateNotification}
import eventstreams.signals._
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class GaugesServiceTest
  extends FlatSpec with GaugesServiceNodeTestContext with SharedActorSystem with NowProvider {


  trait WithNodeStarted extends WithGaugesNode1 {
    clearEvents()

    def mfilterSub(j: String) =
      subscribeFrom1(gauges1System, LocalSubj(ComponentKey("gauges"), TopicKey("mfilter:" + Json.stringify(Json.parse(j)))))

    def lastUpdate = Json.parse(lastUpdateAsStr)

    def lastUpdateAsStr = locateLastEventFieldValue(UpdateReceived, "Data").asInstanceOf[String]


  }

  trait WithMetricsCreated extends WithNodeStarted {
    startMessageSubscriber1(gauges1System)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Syd.Host1~Foo.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Syd.Host1~Foo.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Syd.Host1~Foo.XL~C1~Load"), priority = Some("A")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Syd.Host1~Foo.XL~C2~Load")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A1~Queue"), priority = Some("Z")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A2~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A3~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A4~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A4~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A5~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A6~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A7~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A8~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A9~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A10~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A6~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A7~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A8~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A9~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A10~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host3~Foo.XL~C3~R/s"), priority = Some("A")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host4~Foo.XL~C3~R/s"), priority = Some("C")).evt)
  }


  trait SubscribedToDifferentMetricTypes extends WithNodeStarted {
    startMessageSubscriber1(gauges1System)

    val gauge1SignalName = Some("Syd.Host1~Foo.XL~C1~Gauge")
    val gauge2SignalName = Some("Syd.Host1~Foo.XL~C1~Gauge2")
    val timingSignalName = Some("Syd.Host1~Foo.XL~C1~Timing")
    val stateSignalName = Some("Syd.Host1~Foo.XL~C1~State")
    val occurrenceSignalName = Some("Syd.Host1~Foo.XL~C1~Occurrence")
    val gauge1Signal = new SignalEventFrame("key","seed", name = gauge1SignalName)
    val gauge2Signal = new SignalEventFrame("key","seed", name = gauge2SignalName, metricType = Some(SigMetricTypeGauge()))
    val timingSignal = new SignalEventFrame("key","seed", name = timingSignalName, metricType = Some(SigMetricTypeTiming()))
    val stateSignal = new SignalEventFrame("key","seed", name = stateSignalName, metricType = Some(SigMetricTypeState()))
    val occurrenceSignal = new SignalEventFrame("key","seed", name = occurrenceSignalName, metricType = Some(SigMetricTypeOccurrence()))

    sendEventFrameToGaugeService1(gauge1Signal.evt)
    val gauge1ComponentKey = ComponentKey(locateLastEventFieldValue(GaugesManagerConstants.MetricAdded, "ComponentKey").asInstanceOf[String])
    clearEvents()
    sendEventFrameToGaugeService1(gauge2Signal.evt)
    val gauge2ComponentKey = ComponentKey(locateLastEventFieldValue(GaugesManagerConstants.MetricAdded, "ComponentKey").asInstanceOf[String])
    clearEvents()
    sendEventFrameToGaugeService1(timingSignal.evt)
    val timingComponentKey = ComponentKey(locateLastEventFieldValue(GaugesManagerConstants.MetricAdded, "ComponentKey").asInstanceOf[String])
    clearEvents()
    sendEventFrameToGaugeService1(stateSignal.evt)
    val stateComponentKey = ComponentKey(locateLastEventFieldValue(GaugesManagerConstants.MetricAdded, "ComponentKey").asInstanceOf[String])
    clearEvents()
    sendEventFrameToGaugeService1(occurrenceSignal.evt)
    val occurrenceComponentKey = ComponentKey(locateLastEventFieldValue(GaugesManagerConstants.MetricAdded, "ComponentKey").asInstanceOf[String])
    clearEvents()


    subscribeFrom1(gauges1System, LocalSubj(gauge1ComponentKey, TopicKey("data")))
    subscribeFrom1(gauges1System, LocalSubj(gauge2ComponentKey, TopicKey("data")))
    subscribeFrom1(gauges1System, LocalSubj(timingComponentKey, TopicKey("data")))
    subscribeFrom1(gauges1System, LocalSubj(stateComponentKey, TopicKey("data")))
    subscribeFrom1(gauges1System, LocalSubj(occurrenceComponentKey, TopicKey("data")))
    expectSomeEventsWithTimeout(5000, 5, UpdateReceived, 'Topic -> "data")
    clearEvents()
    mfilterSub( """{"c":[{"s": ["C1"], "q": "*"}],"lim": 5, "limm": 3, "lvl": 2}""")
    expectSomeEventsWithTimeout(5000, UpdateReceived)
    lastUpdateAsStr should include( """"m":[]""")
    clearEvents()
  }


  "Blank mfilter request" should "give up to three results on 1st level for each group, in a particular order with respect to priority" taggedAs OnlyThisTest in new WithMetricsCreated {

    mfilterSub( """
        {
         "lim": 3, "limm": 3
        }
                """)

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")
  }


  it should "get three matching metrics" in new WithMetricsCreated {

    mfilterSub( """
        {
         "lim": 3, "limm": 3
        }
                """)

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }


  "mfilter request without selections" should "give up to three results on 1st level for each group, in a particular order with respect to priority" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")
  }

  it should "get three matching metrics" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "show only the entries matching queries, and it should not affect selected metrics" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "abc"}],
         "s": [{"s": [], "q": "*oo"}],
         "c": [{"s": [], "q": "C*"}],
         "m": [{"s": [], "q": "*ue*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Queue","q":true}],"c":1}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }


  "mfilter" should "handle 'h level 1 selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Syd"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")


    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Syd","q":true},{"n":"Lon","q":true}],"c":2},{"i":[{"n":"Host1","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle 'h level 1 selection with unmatching query on level 1" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Syd"], "q": "abc"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Syd","q":false}],"c":1},{"i":[{"n":"Host1","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle 'h level 1 selection with unmatching query on level 1 and 2" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Syd"], "q": "abc"},{"s": [], "q": "abc"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Syd","q":false}],"c":1},{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle another 'h level 1 selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")


    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }

  it should "handle another 'h level 1 selection - give more components if requested" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 5
        }""")



    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }

  it should "handle another 'h level 1 selection - give more matches and metrics if requested" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 40, "limm": 5
        }""")



    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host2~Foo.XL~C1~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XL~C2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A1~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A4~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A5~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A1~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A4~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A5~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A7~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A8~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A9~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A7~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A8~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A9~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~R/s")
    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Lon.Host4~Foo.XL~C3~R/s")


  }

  it should "handle multiple 'h level 1 selections" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Syd","Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host1","q":true},{"n":"Host2","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "handle multiple 'h level 1 selections - in any order" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Lon", "Syd"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host1","q":true},{"n":"Host2","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "handle invalid selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["LonX"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"s":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"c":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"m":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"m":[]}""")

  }

  it should "handle 's level 1 selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Syd.Host1~Foo.XL~C1~Load")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")


  }

  it should "handle 's level 2 invalid selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"},{"s": ["Baaar"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"c":[{"i":[],"c":0}]""")
    lastUpdateAsStr should include( """"m":[{"i":[],"c":0}]""")

    lastUpdateAsStr should include( """"m":[]}""")


  }


  it should "handle 'h and 's level 1 selections" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 5
        }""")



    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host3~Foo.XL~C3~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }


  it should "handle 'c level 1 selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A10","q":true},{"n":"A6","q":true},{"n":"A7","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Queue","q":true},{"n":"R/s","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A7~Queue")


  }

  it should "handle 'c and 'm level 1 selections" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["Queue"], "q": "R*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A10","q":true},{"n":"A6","q":true},{"n":"A7","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"Queue","q":false},{"n":"R/s","q":true}],"c":2}]""")

    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A10~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A6~Queue")
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.A7~Queue")


  }

  it should "handle 'c and 'm level 1 selections - different selection" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A1~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }

  it should "not return any metrics if none matching required warning level" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"m":[]}""")


  }
  it should "return all metrics matching required warning level" in new WithMetricsCreated {

    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).sigKey.get, 1))
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).sigKey.get, 1))
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).sigKey.get, 1))


    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    expectSomeEventsWithTimeout(5000, UpdateReceived)

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }
  it should "return all metrics matching or higher than required warning level" in new WithMetricsCreated {

    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).sigKey.get, 2))
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).sigKey.get, 2))
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).sigKey.get, 2))


    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    expectSomeEventsWithTimeout(5000, UpdateReceived)

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }

  it should "rebuild and republish filters if level updates" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"m":[]}""")

    clearEvents()
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).sigKey.get, 2))

    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }

    clearEvents()
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).sigKey.get, 2))
    expectSomeEventsWithTimeout(5000, UpdateReceived)

    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")
    lastUpdateAsStr shouldNot include("Lon.Host2~Foo.XB~Actors.A2~R/s")

    clearEvents()
    sendToGaugeService1(LevelUpdateNotification(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).sigKey.get, 2))
    expectSomeEventsWithTimeout(5000, UpdateReceived)

    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A2~R/s")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }

  it should "rebuild and republish filters if new metrics added" in new WithMetricsCreated {

    mfilterSub( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 1, "limm": 3
        }""")

    lastUpdateAsStr should include( """"h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    lastUpdateAsStr should include( """"c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    lastUpdateAsStr should include( """"m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    lastUpdateAsStr should include("Lon.Host2~Foo.XB~Actors.A1~R/s")

    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.A2.XX~R/s"), priority = Some("Z")).evt)

    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }

    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.AB~R/s"), priority = Some("B")).evt)
    expectSomeEventsWithTimeout(5000, UpdateReceived)
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.AB~R/s")

    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = Some("Lon.Host2~Foo.XC~Actors.AC~R/s"), priority = Some("A")).evt)

    expectSomeEventsWithTimeout(5000, UpdateReceived)
    lastUpdateAsStr should include("Lon.Host2~Foo.XC~Actors.AC~R/s")


  }


  "gauge metric" should "should update" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
  }

  it should "affect no other metrics when updated" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    waitAndCheck {
      expectSomeEventsWithTimeout(3000, 1, UpdateReceived)
    }
  }

  it should "reflect the latest value and correctly calculate all values" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(15), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"20.00,15.00,4.08,20.00,20.00""".r)
  }

  it should "not expire the ttlMs was never set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    clearEvents()
    waitWithTimeout(5000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "expire the value after 1.5 seconds if ttlMs=1500" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_,10.00,0.00,10.00,10.00""".r)
  }

  it should "once ttlMs=1500 is set, it would apply to all other updates" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), ttlMs = Some(1500)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(15), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"20.00,15.00,4.08,20.00,20.00""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_,15.00,4.08,20.00,20.00""".r)
  }

  it should "ignore signals out of order" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(20), timestamp = Some(now - 100000)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(15), timestamp = Some(now - 10000)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    clearEvents()
    waitWithTimeout(2000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without timestamp" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10)).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without value" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, timestamp = Some(now)).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }
  }

  it should "stay on level 1 (green) if levels are not set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "stay on level 1 if levels are not set - regardless of the values" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-85), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-50), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(0), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(85), timestamp = Some(now)).evt)
    expectOneOrMoreEvents(UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    waitAndCheck {
      expectNoEvents(UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
      expectNoEvents(UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    }
  }

  it should "stay on level 1 when levels configured like [_,_,60,80] and updates are 10, 59.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(59.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "stay on level 1 when levels configured like [_,_,60.01,80] and updates are 10, 60" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(59.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60,80] and updates are 10, 60" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60.01,80] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60.01,_] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 10, 5" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(5), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 5.01, 4.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(5.01), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(4.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }
  it should "detect level 2 (yellow) when levels configured like [-10,-5,60.01,61] and updates are -4.99,-5.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-4.99), timestamp = Some(now), levels = Some("-10,-5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-5.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60,80], and reconfigured [_,_,50,80] and updates are 50,50" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,50,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,80" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,60,80" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,80.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80.01] and updates are 10,80.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80.01] and updates are 10,80000" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(80000), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [2,5,60,80.01] and updates are 10,2" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(2), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [1.99,5,60,80.01] and updates are 10,2,1.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("1.99,5,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(2), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(1.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,-19,60,80.01] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,-19,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,-19,_,_] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,-19,_,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,_,_,_] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,_,_,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "properly update levels when levels configured like [-20,-19,60,80.01] and updates are 0,10,60,65,90,60,10,-19,-30" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(0), timestamp = Some(now), levels = Some("-20,-19,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(65), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(90), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-19), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(-30), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    clearEvents()
  }

  it should "report unknown level when value expired" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80"), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":0""".r)
  }

  it should "report level changes to the parent which will result in mfilter update to happen" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(60), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s"""${gauge1SignalName.get}""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = gauge1SignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> gauge1ComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    expectSomeEventsWithTimeout(5000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s""""m":\\[\\]""".r)
  }





  "timing metric" should "should update" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
  }

  it should "affect no other metrics when updated" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    waitAndCheck {
      expectSomeEventsWithTimeout(3000, 1, UpdateReceived)
    }
  }

  it should "reflect the latest value and correctly calculate all values" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(15), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"20.00,15.00,4.08,20.00,20.00""".r)
  }

  it should "reflect the latest value and correctly calculate all values - various units" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(0.01), timestamp = Some(now), unit = Some("s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(15), timestamp = Some(now), unit = Some("millis")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(0.02), timestamp = Some(now), unit = Some("sec")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"20.00,15.00,4.08,20.00,20.00""".r)
  }

  it should "not expire the ttlMs was never set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    clearEvents()
    waitWithTimeout(5000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "expire the value after 1.5 seconds if ttlMs=1500" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_,10.00,0.00,10.00,10.00""".r)
  }

  it should "once ttlMs=1500 is set, it would apply to all other updates" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), ttlMs = Some(1500)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(15), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"20.00,15.00,4.08,20.00,20.00""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_,15.00,4.08,20.00,20.00""".r)
  }

  it should "ignore signals out of order" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(20), timestamp = Some(now - 100000)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(15), timestamp = Some(now - 10000)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10.00,10.00,0.00,10.00,10.00""".r)
    clearEvents()
    waitWithTimeout(2000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without timestamp" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10)).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without value" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, timestamp = Some(now)).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }
  }

  it should "stay on level 1 (green) if levels are not set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "stay on level 1 if levels are not set - regardless of the values" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-85), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-50), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(0), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(85), timestamp = Some(now)).evt)
    expectOneOrMoreEvents(UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    waitAndCheck {
      expectNoEvents(UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
      expectNoEvents(UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    }
  }

  it should "stay on level 1 when levels configured like [_,_,60,80] and updates are 10, 59.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(59.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "stay on level 1 when levels configured like [_,_,60.01,80] and updates are 10, 60" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(59.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60,80] and updates are 10, 60" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60.01,80] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60.01,_] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60.01,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 10, 60.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 10, 5" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(5), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [2,5,60.01,61] and updates are 5.01, 4.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(5.01), timestamp = Some(now), levels = Some("2,5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(4.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }
  it should "detect level 2 (yellow) when levels configured like [-10,-5,60.01,61] and updates are -4.99,-5.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-4.99), timestamp = Some(now), levels = Some("-10,-5,60.01,61")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-5.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [_,_,60,80], and reconfigured [_,_,50,80] and updates are 50,50" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,50,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,80" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,60,80" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80] and updates are 10,80.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80.01] and updates are 10,80.01" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80.01), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [_,_,60,80.01] and updates are 10,80000" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(80000), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [2,5,60,80.01] and updates are 10,2" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("2,5,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(2), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [1.99,5,60,80.01] and updates are 10,2,1.99" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("1.99,5,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(2), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(1.99), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,-19,60,80.01] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,-19,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,-19,_,_] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,-19,_,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "detect level 3 (red) when levels configured like [-20,_,_,_] and updates are -19.5,-20" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-19.5), timestamp = Some(now), levels = Some("-20,_,_,_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-20), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }

  it should "properly update levels when levels configured like [-20,-19,60,80.01] and updates are 0,10,60,65,90,60,10,-19,-30" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(0), timestamp = Some(now), levels = Some("-20,-19,60,80.01")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(65), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(90), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-19), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(-30), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    clearEvents()
  }

  it should "report unknown level when value expired" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(10), timestamp = Some(now), levels = Some("_,_,60,80"), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":0""".r)
  }

  it should "report level changes to the parent which will result in mfilter update to happen" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(60), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s"""${timingSignalName.get}""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = timingSignalName, numValue = Some(50), timestamp = Some(now), levels = Some("_,_,60,80")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> timingComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    expectSomeEventsWithTimeout(5000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s""""m":\\[\\]""".r)
  }






  "state metric" should "should update" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"active""".r)
  }

  it should "should pick value from numValue if strValue not available" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"10""".r)
  }

  it should "affect no other metrics when updated" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now)).evt)
    waitAndCheck {
      expectSomeEventsWithTimeout(3000, 1, UpdateReceived)
    }
  }

  it should "reflect the latest value " in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("passive"), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("pending"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"pending""".r)
  }

  it should "not expire the ttlMs was never set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"active""".r)
    clearEvents()
    waitWithTimeout(5000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "expire the value after 1.5 seconds if ttlMs=1500" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"active""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_""".r)
  }

  it should "once ttlMs=1500 is set, it would apply to all other updates" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now), ttlMs = Some(1500)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("pending"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"pending""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"_""".r)
  }

  it should "ignore signals out of order" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active"), timestamp = Some(now)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("passive"), timestamp = Some(now - 100000)).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("pending"), timestamp = Some(now - 10000)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"v":"active""".r)
    clearEvents()
    waitWithTimeout(2000) {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without timestamp" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("active")).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }

  }

  it should "ignore signals without value" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, timestamp = Some(now)).evt)
    waitAndCheck {
      expectNoEvents(UpdateReceived)
    }
  }

  it should "stay on level 1 (green) if levels are not set" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, numValue = Some(10), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }


  it should "stay on level 1 when levels configured like [(^y.*|^o.+),_] and updates are green, pink" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("pink"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
  }

  it should "detect level 2 (yellow) when levels configured like [(^y.*|^o.+),_] and updates are green, yellow" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("yellow"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }
  it should "detect level 2 (yellow) when levels configured like [(^y.*|^o.+),_] and updates are green, orange" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),_")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("orange"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }
  it should "detect level 2 (yellow) when levels configured like [(^y.*|^o.+),^r.*] and updates are green, orange" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("orange"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
  }
  it should "detect level 3 (red) when levels configured like [(^y.*|^o.+),^r.*] and updates are green, red" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("red"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
  }
  it should "properly update levels when levels configured like [(^y.*|^o.+),^r.*] and updates are green, yellow, red, orange, unknown" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("yellow"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("red"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("orange"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":2""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("unknown"), timestamp = Some(now)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    clearEvents()
  }


  it should "report unknown level when value expired" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*"), ttlMs = Some(1500)).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    expectSomeEventsWithTimeout(10000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":0""".r)
  }

  it should "report level changes to the parent which will result in mfilter update to happen" in new SubscribedToDifferentMetricTypes {
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("red"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":3""".r)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s"""${stateSignalName.get}""".r)
    clearEvents()
    sendEventFrameToGaugeService1(new SignalEventFrame("key","seed", name = stateSignalName, strValue = Some("green"), timestamp = Some(now), levels = Some("(^y.*|^o.+),^r.*")).evt)
    expectSomeEventsWithTimeout(3000, 1, UpdateReceived, 'ComponentKey -> stateComponentKey.key, 'Topic -> "data", 'Data -> """"l":1""".r)
    expectSomeEventsWithTimeout(5000, 1, UpdateReceived, 'ComponentKey -> "gauges", 'Data -> s""""m":\\[\\]""".r)
  }


}

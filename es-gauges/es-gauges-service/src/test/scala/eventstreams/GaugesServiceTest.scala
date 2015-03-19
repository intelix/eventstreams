package eventstreams

import eventstreams.Tools.configHelper
import eventstreams.gauges.UpdateLevel
import eventstreams.signals.SignalEventFrame
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class GaugesServiceTest
  extends FlatSpec with GaugesServiceNodeTestContext with SharedActorSystem {


  trait WithNodeStarted extends WithGaugesNode1 {
    clearEvents()

    def mfilterCmd(j: String) = commandFrom1(gauges1System, LocalSubj(ComponentKey("gauges"), TopicKey("mfilter")), Some(Json.parse(j)))

    def cmdResponse = Json.parse(cmdResponseAsStr)

    def cmdResponseAsStr = locateLastEventFieldValue(CommandOkReceived, "Contents").asInstanceOf[String]


  }

  trait WithMetricsCreated extends WithNodeStarted {
    startMessageSubscriber1(gauges1System)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Foo.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Foo.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Foo.XL~C1~Load"), priority = Some("A")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Foo.XL~C2~Load")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A1~Queue"), priority = Some("Z")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A2~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A3~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A4~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A4~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A5~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A6~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A7~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A8~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A9~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A10~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A6~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A7~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A8~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A9~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Foo.XC~Actors.A10~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host3~Foo.XL~C3~R/s"), priority = Some("A")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host4~Foo.XL~C3~R/s"), priority = Some("C")).evt)
  }

  "Blank mfilter request" should "give up to three results on 1st level for each group, in a particular order with respect to priority" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "lim": 3, "limm": 3
        }
                """)

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")
  }

  it should "get three matching metrics" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "lim": 3, "limm": 3
        }
                """)

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }


  "mfilter request without selections" should "give up to three results on 1st level for each group, in a particular order with respect to priority" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")
  }

  it should "get three matching metrics" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "show only the entries matching queries, and it should not affect selected metrics" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "abc"}],
         "s": [{"s": [], "q": "*oo"}],
         "c": [{"s": [], "q": "C*"}],
         "m": [{"s": [], "q": "*ue*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Queue","q":true}],"c":1}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }


  "mfilter" should "handle 'h level 1 selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Syd"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")


    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Syd","q":true},{"n":"Lon","q":true}],"c":2},{"i":[{"n":"Host1","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle 'h level 1 selection with unmatching query on level 1" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Syd"], "q": "abc"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Syd","q":false}],"c":1},{"i":[{"n":"Host1","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle 'h level 1 selection with unmatching query on level 1 and 2" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Syd"], "q": "abc"},{"s": [], "q": "abc"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Syd","q":false}],"c":1},{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C1","q":true},{"n":"C2","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Load","q":true},{"n":"R/s","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C2~Load")

  }

  it should "handle another 'h level 1 selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")


    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }

  it should "handle another 'h level 1 selection - give more components if requested" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 5
        }""")



    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }

  it should "handle another 'h level 1 selection - give more matches and metrics if requested" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 40, "limm": 5
        }""")



    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host2~Foo.XL~C1~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XL~C2~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A1~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A2~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A4~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A5~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A1~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A2~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A3~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A4~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A5~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A7~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A8~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A9~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A7~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A8~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A9~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~R/s")
    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Lon.Host4~Foo.XL~C3~R/s")




  }

  it should "handle multiple 'h level 1 selections" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Syd","Lon"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host1","q":true},{"n":"Host2","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "handle multiple 'h level 1 selections - in any order" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Lon", "Syd"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host1","q":true},{"n":"Host2","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")

  }

  it should "handle invalid selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["LonX"], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""s":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""c":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""m":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""m":[]}""")

  }

  it should "handle 's level 1 selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"C1","q":true},{"n":"Actors","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Load","q":true},{"n":"Queue","q":true}],"c":3}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Syd.Host1~Foo.XL~C1~Load")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")


  }

  it should "handle 's level 2 invalid selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"},{"s": ["Baaar"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""c":[{"i":[],"c":0}]""")
    cmdResponseAsStr should include (""""m":[{"i":[],"c":0}]""")

    cmdResponseAsStr should include (""""m":[]}""")


  }


  it should "handle 'h and 's level 1 selections" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": ["Lon"], "q": "*"}],
         "s": [{"s": ["Foo"], "q": "*"}],
         "c": [{"s": [], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 5
        }""")



    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true},{"n":"Syd","q":true}],"c":2},{"i":[{"n":"Host3","q":true},{"n":"Host2","q":true},{"n":"Host4","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1},{"i":[{"n":"XL","q":true},{"n":"XC","q":true},{"n":"XB","q":true}],"c":3}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"C3","q":true},{"n":"Actors","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true},{"n":"Queue","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host3~Foo.XL~C3~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")

  }


  it should "handle 'c level 1 selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": [], "q": "*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A10","q":true},{"n":"A6","q":true},{"n":"A7","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Queue","q":true},{"n":"R/s","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A7~Queue")


  }

  it should "handle 'c and 'm level 1 selections" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["Queue"], "q": "R*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A10","q":true},{"n":"A6","q":true},{"n":"A7","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"Queue","q":false},{"n":"R/s","q":true}],"c":2}]""")

    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A10~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A6~Queue")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XC~Actors.A7~Queue")


  }

  it should "handle 'c and 'm level 1 selections - different selection" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A1~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A2~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }

  it should "not return any metrics if none matching required warning level" in new WithMetricsCreated {

    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""m":[]}""")


  }
  it should "not return all metrics matching required warning level" in new WithMetricsCreated {

    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).sigKey.get, 1))
    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).sigKey.get, 1))
    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).sigKey.get, 1))


    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A2~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }
  it should "not return all metrics matching or higher than required warning level" in new WithMetricsCreated {

    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A5~Queue")).sigKey.get, 2))
    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A2~R/s")).sigKey.get, 2))
    sendToGaugeService1(UpdateLevel(new SignalEventFrame(name = Some("Lon.Host2~Foo.XB~Actors.A3~R/s")).sigKey.get, 2))


    mfilterCmd( """
        {
         "h": [{"s": [], "q": "*"}],
         "s": [{"s": [], "q": "*"}],
         "c": [{"s": ["Actors"], "q": "*"}],
         "m": [{"s": ["R/s"], "q": "R*"}],
         "lim": 3, "limm": 3, "lvl": 1
        }""")

    cmdResponseAsStr should include (""""h":[{"i":[{"n":"Lon","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""s":[{"i":[{"n":"Foo","q":true}],"c":1}]""")
    cmdResponseAsStr should include (""""c":[{"i":[{"n":"Actors","q":true},{"n":"C3","q":true},{"n":"C1","q":true},{"n":"C2","q":true}],"c":4},{"i":[{"n":"A1","q":true},{"n":"A2","q":true},{"n":"A3","q":true}],"c":10}]""")
    cmdResponseAsStr should include (""""m":[{"i":[{"n":"R/s","q":true}],"c":1}]""")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A2~R/s")
    cmdResponseAsStr should include ("Lon.Host2~Foo.XB~Actors.A3~R/s")


  }

}

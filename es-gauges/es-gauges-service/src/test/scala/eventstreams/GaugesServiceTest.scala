package eventstreams

import eventstreams.signals.SignalEventFrame
import eventstreams.support._
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class GaugesServiceTest
  extends FlatSpec with GaugesServiceNodeTestContext with SharedActorSystem {


  trait WithNodeStarted extends WithGaugesNode1 {
    clearEvents()
  }

  "Test test" should "start when accepting connection" in new WithNodeStarted {
    startMessageSubscriber1(gauges1System)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Icon.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Icon.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Icon.XL~C1~Load"), priority = Some("A")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Syd.Host1~Icon.XL~C2~Load")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XL~C1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XL~C2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A1~Queue"), priority = Some("Z")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A2~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A3~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A4~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A5~Queue")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A1~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A2~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A3~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A4~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XB~Actors.A5~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A6~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A7~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A8~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A9~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A10~Queue"), priority = Some("B")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A6~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A7~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A8~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A9~R/s")).evt)
    sendEventFrameToGaugeService1(new SignalEventFrame(name = Some("Lon.Host2~Icon.XC~Actors.A10~R/s")).evt)

    val j =
      """
        |{
        | "h": [
        |   {"s": ["Syd","Lon"], "q": "*"},
        |   {"s": [], "q": "*"}
        | ],
        | "s": [
        |   {"s": [], "q": "*"}
        | ],
        | "c": [
        |   {"s": ["Actors"], "q": "*"},
        |   {"s": [], "q": "*3"}
        | ],
        | "m": [
        |   {"s": [], "q": "*"}
        | ],
        | "lim": 3
        |}
      """.stripMargin
    val tpl =
      """
        |{
        | "h": [
        |   {"s": ["Syd","h2"], "q": "*"},
        |   {"s": ["h3","h4"], "q": "*"}
        | ],
        | "s": [
        |   {"s": ["s1","s2"], "q": "*"}
        | ],
        | "c": [
        |   {"s": ["c1","c2"], "q": "*"}
        | ],
        | "m": [
        |   {"s": ["m1","m2"], "q": "*"}
        | ]
        |}
      """.stripMargin

    commandFrom1(gauges1System, LocalSubj(ComponentKey("gauges"), TopicKey("mfilter")), Some(Json.parse(j)))

    collectAndPrintEvents()
  }

}

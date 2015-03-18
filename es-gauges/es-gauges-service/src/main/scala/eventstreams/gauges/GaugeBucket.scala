package eventstreams.gauges

import eventstreams.signals.SignalEventFrame
import play.api.libs.json.JsValue

trait GaugeBucket {
  def update(frame: SignalEventFrame) = ???
  def toData: Option[JsValue] = ???

}

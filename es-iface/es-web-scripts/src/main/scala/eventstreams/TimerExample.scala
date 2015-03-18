package eventstreams

import japgolly.scalajs.react._, vdom.prefix_<^._
import scala.scalajs.js

/** Scala version of "A Stateful Component" on http://facebook.github.io/react/ */
object TimerExample {

  case class State(secondsElapsed: Long)

  class Backend($: BackendScope[_, State]) {
    var interval: js.UndefOr[js.timers.SetIntervalHandle] =
      js.undefined

    def tick() =
      $.modState(s => State(s.secondsElapsed + 1))

    def start() =
      interval = js.timers.setInterval(1000)(tick())
  }

  val Timer = ReactComponentB[Unit]("Timer")
    .initialState(State(0))
    .backend(new Backend(_))
    .render($ => <.div("Seconds elapsed: ", $.state.secondsElapsed))
    .componentDidMount(_.backend.start())
    .componentWillUnmount(_.backend.interval foreach js.timers.clearInterval)
    .buildU
}

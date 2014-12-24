package core.events

import scala.language.implicitConversions

sealed trait Event {
  def id: String
  def >>(f1: (EventFieldWithValue)*)(implicit ctx: WithEvents, publisher: EventPublisher, component: CtxComponent, system: CtxSystem) = publisher.publish(this, f1)
}

object EventOps {
  var map = Map[Symbol, SimpleField]()

  implicit def stringToEventOps(s: String): EventOps = new EventOps(s)
  implicit def symbolToEventOps(s: Symbol): EventOps = new EventOps(s.name)

  implicit def symbolToEventField(s: Symbol) : EventField = map.getOrElse(s, {
    val v = SimpleField(s.name)
    map = map + (s -> v)
    v
  })

}

class EventOps(id: String) {
  def trace : Event = TraceEvent(id)
  def info : Event = InfoEvent(id)
  def warn : Event = WarnEvent(id)
  def error : Event = ErrorEvent(id)

}

case class TraceEvent(id: String) extends Event
case class InfoEvent(id: String) extends Event
case class WarnEvent(id: String) extends Event
case class ErrorEvent(id: String) extends Event




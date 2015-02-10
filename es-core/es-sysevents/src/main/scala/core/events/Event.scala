package core.events

import scala.language.implicitConversions

sealed trait Event {
  
  def id: String
  def componentId: String
  def >>>(f1: => Seq[FieldAndValue])(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = ctx.evtPublisher.publish(system, this, ctx.commonFields match {
    case x if x.isEmpty => f1
    case x => f1 ++ x
  })
  def >>()(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq())
  def >>(f1: => FieldAndValue)(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq(f1))
  def >>(f1: => FieldAndValue, f2: => FieldAndValue)(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq(f1, f2))
  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue)(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq(f1, f2, f3))
  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue, f4: => FieldAndValue)(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq(f1, f2, f3, f4))
  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue, f4: => FieldAndValue, f5: => FieldAndValue)(implicit ctx: WithEventPublisher, system: CtxSystem): Unit = >>> (Seq(f1, f2, f3, f4,f5))
}

object EventOps {
  implicit def stringToEventOps(s: String)(implicit component: CtxComponent): EventOps = new EventOps(s, component)
  implicit def symbolToEventOps(s: Symbol)(implicit component: CtxComponent): EventOps = new EventOps(s.name, component)
}

class EventOps(id: String, component: CtxComponent) {
  def trace : Event = TraceEvent(id, component.componentId)
  def info : Event = InfoEvent(id, component.componentId)
  def warn : Event = WarnEvent(id, component.componentId)
  def error : Event = ErrorEvent(id, component.componentId)

}

case class TraceEvent(id: String, componentId: String) extends Event
case class InfoEvent(id: String, componentId: String) extends Event
case class WarnEvent(id: String, componentId: String) extends Event
case class ErrorEvent(id: String, componentId: String) extends Event




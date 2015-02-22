package core.sysevents

import scala.language.implicitConversions

sealed trait Sysevent {

  def id: String

  def componentId: String

  def >>>(f1: => Seq[FieldAndValue])(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = ctx.evtPublisher.publish(system, this, ctx.commonFields match {
    case x if x.isEmpty => f1
    case x => f1 ++ x
  })

  def >>()(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq())

  def >>(f1: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1))

  def >>(f1: => FieldAndValue, f2: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1, f2))

  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1, f2, f3))

  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue, f4: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1, f2, f3, f4))

  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue, f4: => FieldAndValue, f5: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1, f2, f3, f4, f5))

  def >>(f1: => FieldAndValue, f2: => FieldAndValue, f3: => FieldAndValue, f4: => FieldAndValue, f5: => FieldAndValue, f6: => FieldAndValue)(implicit ctx: WithSyseventPublisher, system: SyseventSystem): Unit = >>>(Seq(f1, f2, f3, f4, f5, f6))
}

object SyseventOps {
  implicit def stringToSyseventOps(s: String)(implicit component: SyseventComponent): SyseventOps = new SyseventOps(s, component)

  implicit def symbolToSyseventOps(s: Symbol)(implicit component: SyseventComponent): SyseventOps = new SyseventOps(s.name, component)
}

class SyseventOps(id: String, component: SyseventComponent) {
  def trace: Sysevent = TraceSysevent(id, component.componentId)

  def info: Sysevent = InfoSysevent(id, component.componentId)

  def warn: Sysevent = WarnSysevent(id, component.componentId)

  def error: Sysevent = ErrorSysevent(id, component.componentId)

}

case class TraceSysevent(id: String, componentId: String) extends Sysevent

case class InfoSysevent(id: String, componentId: String) extends Sysevent

case class WarnSysevent(id: String, componentId: String) extends Sysevent

case class ErrorSysevent(id: String, componentId: String) extends Sysevent




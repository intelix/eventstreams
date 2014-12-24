package core.events

import scala.language.implicitConversions


trait WithEvents  {

  implicit lazy val sys = CtxSystemRef.ref
  implicit lazy val pub = EventPublisherRef.ref

  implicit lazy val evtCtx = this

  def commonFields: Seq[EventFieldWithValue] = Seq()

}


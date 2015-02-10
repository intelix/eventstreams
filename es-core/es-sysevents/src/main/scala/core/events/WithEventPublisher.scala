package core.events

import scala.language.implicitConversions


trait WithEventPublisher  {

  implicit lazy val evtSystem = CtxSystemRef.ref
  implicit lazy val evtPublisher = EventPublisherRef.ref

  implicit lazy val evtCtx = this

  def commonFields: Seq[FieldAndValue] = Seq()

}


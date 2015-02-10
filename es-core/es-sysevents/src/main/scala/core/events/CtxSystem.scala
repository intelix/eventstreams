package core.events

trait CtxSystem {
  def id: String
}

case class EvtSystem(id: String) extends CtxSystem

object CtxSystemRef {
  implicit var ref = EvtSystem("default")
}
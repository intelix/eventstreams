package core.events

trait CtxComponent {

  implicit val component = this

  def id: String
}

object MyComponents {
  val Component1 = SimpleComponent("Component1")
}

case class SimpleComponent(id: String) extends CtxComponent

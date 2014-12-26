package core.events

trait CtxComponent {

  implicit val component = this

  // TODO REMOVE DEFAULT VALUE!!!!!
  def componentId: String = getClass.getName.substring(getClass.getName.lastIndexOf('.')+1)
}

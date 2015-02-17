package eventstreams

import akka.actor.ActorRef


trait Model {

  def id: Any
  def ref: ActorRef
  def name: String
  
  def sortBy = name

  override def equals(obj: scala.Any): Boolean = obj match {
    case Nil => false
    case null => false
    case x: Model => id == x.id
    case x => x.equals(obj)
  }
}

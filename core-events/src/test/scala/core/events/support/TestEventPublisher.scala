package core.events.support

import core.events._

import scala.collection.mutable

case class RaisedEvent(event: Event, values: Seq[FieldAndValue])

class TestEventPublisher extends EventPublisher with LoggerEventPublisher {

  val events = mutable.Map[Event, List[Seq[FieldAndValue]]]()
  private var eventsInOrder = List[RaisedEvent]()

  def withOrderedEvents(f: List[RaisedEvent] => Unit) = events.synchronized {
    f(eventsInOrder)
  }

  def clear() = events.synchronized {
    events.clear()
    eventsInOrder = List()
  }


  override def publish(system: CtxSystem, event: Event, values: => Seq[FieldAndValue]): Unit = {
    events.synchronized {
      eventsInOrder = eventsInOrder :+ RaisedEvent(event, values)
      events += (event -> (events.getOrElse(event, List()) :+ values.map {
        case (f, v) => f -> transformValue(v)
      }))
    }
    super.publish(system, event, values)
  }

}





package core.events.support

import core.events._

import scala.collection.mutable

case class RaisedEvent(event: Event, values: Seq[EventFieldWithValue])
class TestEventPublisher extends EventPublisher with LoggerEventPublisher {

  val events = mutable.Map[Event, List[Seq[EventFieldWithValue]]]()
  var eventsInOrder = List[RaisedEvent]()

  def clear() = events.synchronized { 
    events.clear()
    eventsInOrder = List()
  }


  override def publish(event: Event, values: => Seq[EventFieldWithValue])(implicit runtimeCtx: WithEventPublisher, system: CtxSystem): Unit = {
    events.synchronized {
      eventsInOrder = eventsInOrder :+ RaisedEvent(event, values)
      events += (event -> (events.getOrElse(event, List()) :+ (values ++ runtimeCtx.commonFields).map {
        case x: SimpleEventFieldWithValue => new SimpleEventFieldWithValue(x.fieldName, transformValue(x.value))
      }))
    }
    super.publish(event, values)
  }

}





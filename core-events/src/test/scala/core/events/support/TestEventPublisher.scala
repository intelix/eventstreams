package core.events.support

import core.events._

class TestEventPublisher extends EventPublisher with LoggerEventPublisher {

  var events = Map[Event, List[Seq[EventFieldWithValue]]]()

  def clear() = events = Map()


  override def publish(event: Event, values: => Seq[EventFieldWithValue])(implicit runtimeCtx: WithEvents, system: CtxSystem): Unit = {
    events = events + (event -> (events.getOrElse(event, List()) :+ (values ++ runtimeCtx.commonFields).map {
      case x: SimpleEventFieldWithValue => new SimpleEventFieldWithValue(x.fieldName, transformValue(x.value))
    }))
    super.publish(event, values)
  }

}





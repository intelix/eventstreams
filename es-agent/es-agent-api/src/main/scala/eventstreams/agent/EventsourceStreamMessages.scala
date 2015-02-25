package eventstreams.agent


trait EventsourceStreamMessages

case class Acknowledged[T](correlationId: Long, msg: T) extends EventsourceStreamMessages
case class StreamClosed() extends EventsourceStreamMessages
case class StreamClosedWithError(msg: Option[String]) extends EventsourceStreamMessages

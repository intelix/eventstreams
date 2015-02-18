package eventstreams.agent

case class Acknowledged[T](correlationId: Long, msg: T)
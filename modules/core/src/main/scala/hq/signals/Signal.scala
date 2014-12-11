package hq.signals

case class Signal(ts: Long,
                  eventId: String,
                  level: SignalLevel,
                  signalClass: String,
                  signalSubclass: Option[String],
                  correlationId: Option[String],
                  title: Option[String],
                  body: Option[String],
                  icon: Option[String])

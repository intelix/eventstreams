package hq.signals

case class Signal(signalId: String,
                  sequenceId: Long,
                  ts: Long,
                  eventId: String,
                  level: SignalLevel,
                  signalClass: String,
                  signalSubclass: Option[String],
                  correlationId: Option[String],
                  transactionDemarcation: Option[String],
                  transactionStatus: Option[String],
                  title: Option[String],
                  body: Option[String],
                  icon: Option[String])

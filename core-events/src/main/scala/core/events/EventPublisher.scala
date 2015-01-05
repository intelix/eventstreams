package core.events

import org.slf4j._
import play.api.libs.json._

trait EventPublisher {
  def publish(system: CtxSystem, event: Event, values: => Seq[FieldAndValue])
}

object LoggerEventPublisherHelper {
  val logFormat = "%10s : %35s - %-25s : %s"

  def log(event: Event, system: CtxSystem, values: Seq[FieldAndValue], f: String => Unit) = {

    def formatNextField(f: FieldAndValue) = f._1.name + "=" + f._2

    val fields = values.foldLeft("") {
      (aggr, next) => aggr + formatNextField(next) + "  "
    }

    val string = logFormat.format(system.id, event.componentId, event.id, fields)

    f(string)
  }
}

trait LoggerEventPublisher {

  var loggers: Map[String, Logger] = Map()

  def loggerFor(s: String) = loggers.get(s) match {
    case Some(x) => x
    case None =>
      val logger: Logger = LoggerFactory.getLogger(s)
      loggers = loggers + (s -> logger)
      logger
  }

  def transformValue(v: Any): Any = v match {
    case JsString(s) => s
    case JsNumber(n) => n.toString()
    case JsBoolean(b) => b.toString
    case b@JsObject(_) => Json.stringify(b)
    case JsArray(arr) => arr.seq.mkString(",")
    case JsNull => ""
    case JsUndefined() => ""
    case s: String => s
    case n: Number => n
    case n: Long => n
    case n: Int => n
    case n: Double => n
    case b: Boolean => b
    case b: JsValue => Json.stringify(b)
    case other => String.valueOf(other)

  }


  def publish(system: CtxSystem, event: Event, values: => Seq[FieldAndValue]) = {
    val logger = loggerFor("events." + system.id + "." + event.componentId + "." + event.id)
    event match {
      case x: TraceEvent if logger.isDebugEnabled =>
        LoggerEventPublisherHelper.log(event, system, values, s => logger.debug(s))
      case x: InfoEvent if logger.isInfoEnabled =>
        LoggerEventPublisherHelper.log(event, system, values, s => logger.info(s))
      case x: WarnEvent if logger.isWarnEnabled =>
        LoggerEventPublisherHelper.log(event, system, values, s => logger.warn(s))
      case x: ErrorEvent if logger.isErrorEnabled =>
        LoggerEventPublisherHelper.log(event, system, values, s => logger.error(s))
      case _ => ()
    }
  }


}

object EventPublisherRef {
  implicit var ref: EventPublisher = LoggerEventPublisher
}

object LoggerEventPublisher extends EventPublisher with LoggerEventPublisher {


}

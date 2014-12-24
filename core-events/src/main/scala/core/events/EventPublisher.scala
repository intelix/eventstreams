package core.events

import org.slf4j._
import play.api.libs.json._

trait EventPublisher {
  def publish(event: Event, values: => Seq[EventFieldWithValue])(implicit runtimeCtx: WithEvents, component: CtxComponent, system: CtxSystem)
}

trait LoggerEventPublisher {
  val logFormat = "%10s : %35s - %-25s : %s"

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
    case JsObject(b) => b.toString()
    case JsArray(arr) => arr.seq.mkString(",")
    case JsNull => ""
    case JsUndefined() => ""
    case s: String => s
    case n: Number => n
    case n: Long => n
    case n: Int => n
    case n: Double => n
    case b: Boolean => b
    case other => String.valueOf(other)

  }


  def log(event: Event, component: CtxComponent, system: CtxSystem, values: Seq[EventFieldWithValue], f: String => Unit) = {

    def formatNextField(f: EventFieldWithValue) = f.fieldName+"="+f.value

    val fields = values.foldLeft("") {
      (aggr, next) => aggr + formatNextField(next)+"  "
    }

    val string = logFormat.format(system.id, component.id, event.id, fields)

    f(string)
  }


  private def process(event: Event, runtimeCtx: WithEvents, component: CtxComponent, system: CtxSystem, values: Seq[EventFieldWithValue], f: String => Unit) = {

    val allValues = runtimeCtx.commonFields match {
      case x if x.isEmpty => values
      case x => values ++ x
    }
    
    log(event, component, system, allValues, f)
  }

  def publish(event: Event, values: => Seq[EventFieldWithValue])(implicit runtimeCtx: WithEvents, component: CtxComponent, system: CtxSystem) = {
    val logger = loggerFor("events."+system.id + "." + component.id + "." + event.id)
    event match {
      case x: TraceEvent if logger.isDebugEnabled => process(event, runtimeCtx, component, system, values, s => logger.debug(s))
      case x: InfoEvent if logger.isInfoEnabled => process(event, runtimeCtx, component, system, values, s => logger.info(s))
      case x: WarnEvent if logger.isWarnEnabled => process(event, runtimeCtx, component, system, values, s => logger.warn(s))
      case x: ErrorEvent if logger.isErrorEnabled => process(event, runtimeCtx, component, system, values, s => logger.error(s))
      case _ => ()
    }
  }


}

object EventPublisherRef {
  implicit var ref: EventPublisher = LoggerEventPublisher
}

object LoggerEventPublisher extends EventPublisher with LoggerEventPublisher {


}

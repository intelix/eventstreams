package core.events.support

import com.typesafe.scalalogging.StrictLogging
import core.events._
import org.scalatest.concurrent.AsyncAssertions.Waiter
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, Matchers}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationDouble

trait EventAssertions extends Matchers with EventMatchers with BeforeAndAfterEach with StrictLogging {
  self: org.scalatest.Suite =>

  EventPublisherRef.ref = new TestEventPublisher()
  CtxSystemRef.ref = EvtSystem("test")

  def clearEvents() =
    EventPublisherRef.ref.asInstanceOf[TestEventPublisher].clear()


  override protected def afterEach(): Unit = {
    clearEvents()
    super.afterEach()
  }

  def events = EventPublisherRef.ref.asInstanceOf[TestEventPublisher].events

  override protected def beforeEach(): Unit = {
    super.beforeEach()
  }

  implicit val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(15, Millis)))


  def waitWithTimeout(millis: Long)(f: => Unit) = {
    val startedAt = System.currentTimeMillis()
    var success = false
    while (!success && System.currentTimeMillis() - startedAt < millis) {
      try {
        f
        success = true
      } catch {
        case x: Throwable => Thread.sleep(15)
      }
    }
    try {
      f
    } catch {
      case x: Throwable =>
        val log = LoggerFactory.getLogger("events")
        log.error("*" * 120 + "\nTest failed", x)
        log.error("Raised events:")
        EventPublisherRef.ref.asInstanceOf[TestEventPublisher].withOrderedEvents { events =>
          events.foreach { next =>
            LoggerEventPublisherHelper.log(next.event, CtxSystemRef.ref, next.values, s => log.error(s))
          }
        }
        log.error("*" * 120 + "\n\n", x)
        throw x
    }
  }

  def expectSomeEvents(event: Event, values: EventFieldWithValue*) = {
    waitWithTimeout(5000) {
      events should contain key event
      events.get(event).get should haveAllValues(values)
    }
  }

  def locateAllEvents(event: Event) = events.get(event)
  def locateLastEvent(event: Event) = locateAllEvents(event).map(_.head)
  def locateFirstEvent(event: Event) = locateAllEvents(event).map(_.head)
  def locateFirstEventFieldValue(event: Event, field: String) = {
    val maybeValue = for (
      all <- locateAllEvents(event);
      first = all.head;
      fieldWrapper <- first.find(_.fieldName == field)
    ) yield fieldWrapper.value
    maybeValue.get
  }
  def locateLastEventFieldValue(event: Event, field: String) = {
    val maybeValue = for (
      all <- locateAllEvents(event);
      first = all.last;
      fieldWrapper <- first.find(_.fieldName == field)
    ) yield fieldWrapper.value
    maybeValue.get
  }

  def waitAndCheck(f: => Unit) = duringPeriodInMillis(500)(f)

  def duringPeriodInMillis(millis: Long)(f: => Unit) = {
    val startedAt = System.currentTimeMillis()
    while (System.currentTimeMillis() - startedAt < millis) {
      f
      Thread.sleep(50)
    }

  }

  def expectNoEvents(event: Event, values: EventFieldWithValue*) =
    events.get(event).foreach(_ shouldNot haveAllValues(values))


  def expectSomeEvents(count: Int, event: Event, values: EventFieldWithValue*) = {
    waitWithTimeout(5000) {
      events should contain key event
      events.get(event).get should haveAllValues(count, values)
    }
  }

}

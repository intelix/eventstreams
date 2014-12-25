package core.events.support

import core.events._
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{SuiteMixin, FlatSpec, BeforeAndAfterEach, Matchers}

trait EventAssertions extends Matchers with EventMatchers with BeforeAndAfterEach {
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

  def expectAnyEvent(event: Event, values: EventFieldWithValue*) = {
    eventually {
      events should contain key event

      if (values.length > 0) {
        events.get(event).get should haveAllValues(values)

      }

    }
  }

  def expectAnyEvent(count: Int, event: Event, values: EventFieldWithValue*) = {
    eventually {
      events should contain key event

      if (values.length > 0) {
        events.get(event).get should haveAllValues(count, values)

      }

    }
  }

}

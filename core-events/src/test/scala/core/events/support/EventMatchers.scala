package core.events.support

import core.events._
import org.scalatest.matchers.{MatchResult, Matcher}

trait EventMatchers {

  class ContainsAllFields(count: Option[Int], values: Seq[EventFieldWithValue]) extends Matcher[List[Seq[EventFieldWithValue]]] {
    def apply(left: List[Seq[EventFieldWithValue]]) = {
      val found = left.count(next =>
        !values.exists { k =>
          !next.exists { v =>
            v.fieldName == k.fieldName && v.value == k.value
          }
        })
      MatchResult(
        count match {
          case Some(req) => found == req
          case None => found > 0
        },
        s"No event with provided field values $values", s"Found event with provided field values $values"
      )
    }
  }

  def haveAllValues(count: Int, values: Seq[EventFieldWithValue]) = new ContainsAllFields(Some(count), values)
  def haveAllValues(values: Seq[EventFieldWithValue]) = new ContainsAllFields(None, values)
}

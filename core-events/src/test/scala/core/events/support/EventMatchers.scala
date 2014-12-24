package core.events.support

import core.events._
import org.scalatest.matchers.{MatchResult, Matcher}

trait EventMatchers {

  class ContainsAllFields(values: Seq[EventFieldWithValue]) extends Matcher[List[Seq[EventFieldWithValue]]] {
    def apply(left: List[Seq[EventFieldWithValue]]) = {
      MatchResult(
        left.exists { next =>
          !values.exists { k =>
            !next.exists { v =>
              v.fieldName == k.fieldName && v.value == k.value
            }
          }
        },
        s"No event with provided field values $values", s"Found event with provided field values $values"
      )
    }
  }

  def haveAllValues(values: Seq[EventFieldWithValue]) = new ContainsAllFields(values)
}

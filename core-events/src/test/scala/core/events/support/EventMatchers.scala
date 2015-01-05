package core.events.support

import core.events._
import org.scalatest.matchers.{MatchResult, Matcher}

trait EventMatchers {

  class ContainsAllFields(count: Option[Int], values: Seq[FieldAndValue]) extends Matcher[List[Seq[FieldAndValue]]] {
    def apply(left: List[Seq[FieldAndValue]]) = {
      val found = if (values.isEmpty) left.size else left.count(next =>
        !values.exists { k =>
          !next.exists { v =>
            v._1 == k._1 && v._2 == k._2
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

  def haveAllValues(count: Int, values: Seq[FieldAndValue]) = new ContainsAllFields(Some(count), values)
  def haveAllValues(values: Seq[FieldAndValue]) = new ContainsAllFields(None, values)
}

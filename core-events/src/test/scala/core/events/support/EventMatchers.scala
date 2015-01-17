package core.events.support

import core.events._
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.util.matching.Regex

trait EventMatchers {

  class ContainsAllFields(count: Option[Int], values: Seq[FieldAndValue]) extends Matcher[List[Seq[FieldAndValue]]] {
    def apply(left: List[Seq[FieldAndValue]]) = {
      val found = if (values.isEmpty) left.size
      else left.count(next =>
        !values.exists { k =>
          !next.exists { v =>
            v._1 == k._1 && (k._2 match {
              case x: Regex => x.findFirstMatchIn(v._2.toString).isDefined
              case x: (String => Boolean) => x(v._2.toString)
              case x => v._2 == x
            })
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

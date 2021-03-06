/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eventstreams

import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json._

import eventstreams.Tools._
import scala.collection.mutable
import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


sealed trait Condition extends StrictLogging {
  type CheckResult = \/[Fail, OK]

  def metFor(frame: EventFrame): CheckResult
}

private object Support {
  val regexCache = mutable.Map[String, Regex]()
  def regexFor(s: Option[String]) : Option[Regex] = s.map { key => regexCache.getOrElseUpdate(key, new Regex(key)) }
}

object SimpleCondition extends StrictLogging {


  val tagRegex = "#(.+)".r
  val isNot = "(.+?)!=(.+)".r
  val is = "(.*?[^!])=(.+)".r
  val isLess = "(.+?)<(.+)".r
  val isMore = "(.+?)>(.+)".r

  def conditionOrAlwaysTrue(optStr: Option[String]): Option[Condition] = {
    SimpleCondition(optStr) match {
      case Some(\/-(cond)) => Some(cond)
      case _ => Some(Condition.alwaysTrue)
    }
  }

  def optionalCondition(optStr: Option[String]): Option[Condition] = {
    SimpleCondition(optStr) match {
      case Some(\/-(cond)) => Some(cond)
      case _ => None
    }
  }

  def apply(optStr: Option[String]): Option[\/[Fail, Condition]] = {

    optStr match {
      case None => None
      case Some(x) if x.trim.isEmpty => None
      case Some(s) =>

        val splitByOr = s.replace('\n',' ').replace('\r',' ').split("(?i)( or )")

        val orArr = splitByOr.map { eachOr =>
          val splitByAnd = eachOr.split("(?i)( and )")

          val arr = splitByAnd.map { eachAnd =>
            for (
              x <- eachAnd.trim match {
                case isNot(a, b) => \/-(("isnot", a, b))
                case is(a, b) => \/-(("is", a, b))
                case isLess(a, b) => \/-(("isless", a, b))
                case isMore(a, b) => \/-(("ismore", a, b))
                case x => Fail(s"Invalid expression $x source ${eachAnd.trim}")
              };
              (cond, fieldOrTag, value) = x;
              y <- fieldOrTag.trim match {
                case tagRegex(name) => ("tag", name).right
                case name => ("field", name).right
              };
              (cl, nm) = y
            ) yield Json.obj(
              "class" -> cl,
              "name" -> nm,
              "value" -> value.trim,
              "cond" -> cond
            )
          }

          arr.toSeq.foldLeft[\/[Fail, List[JsValue]]](\/-(List())) {
            (aggr, next) =>
              aggr.flatMap { list =>
                next match {
                  case -\/(f) => f.left
                  case \/-(obj) => \/-(list :+ obj)
                }
              }
          } map { list =>
            Json.obj(
              "class" -> "all",
              "list" -> Json.toJson(list.toArray)
            )
          }
        }

        val wrappedCfg = orArr.toSeq.foldLeft[\/[Fail, List[JsValue]]](\/-(List())) {
          (aggr, next) =>
            aggr.flatMap { list =>
              next match {
                case -\/(f) => f.left
                case \/-(obj) => \/-(list :+ obj)
              }
            }
        } map { list =>
          Json.obj(
            "class" -> "any",
            "list" -> Json.toJson(list.toArray)
          )
        }


        Some(for (
          cfg <- wrappedCfg;
          cond <- Condition(Some(cfg))
        ) yield cond)

    }
  }
}

case class AlwaysTrueCondition() extends Condition {
  override def metFor(frame: EventFrame): CheckResult = OK("always true condition")
}

case class NeverTrueCondition() extends Condition {
  override def metFor(frame: EventFrame): CheckResult = Fail("always false condition")
}

private case class AnyCondition(conditions: Seq[Condition]) extends Condition {
  override def metFor(frame: EventFrame): CheckResult =
    conditions.map(_.metFor(frame)).collectFirst {
      case c if c.isRight => c
    } | Fail(s"All conditions failed for $frame")
}

private case class AllCondition(conditions: Seq[Condition]) extends Condition {
  override def metFor(frame: EventFrame): CheckResult =
    conditions.map(_.metFor(frame)).collectFirst {
      case c if c.isLeft => c
    } | OK()
}

private case class FieldCondition(name: String, criteriaValue: Option[String], criteriaCondition: Option[String]) extends Condition {
  override def metFor(frame: EventFrame): CheckResult =
    checkConditions(locateRawFieldValue(frame, macroReplacement(frame, name), ""))

  def checkConditions(valueToCheck: EventData): CheckResult =
    criteriaCondition match {
      case None => OK("condition not defined. skipped")
      case Some("is") => criteriaValue match {
        case Some(expectedValue) => valueToCheck match {
          case EventDataValueNumber(numericValueToCheck) => Try {
            if (numericValueToCheck ==  BigDecimal(expectedValue))
              OK(s"'is' condition succeeded: $numericValueToCheck == $expectedValue")
            else
              Fail(s"'is' condition failed: $numericValueToCheck is not == $expectedValue")
          }.recover {
            case _ => Fail(s"'is' condition failed: Unparsable number $expectedValue in criteria")
          }.get
          case EventDataValueBoolean(booleanValueToCheck) => Try {
            if (booleanValueToCheck ==  expectedValue.toBoolean)
              OK(s"'is' condition succeeded: $booleanValueToCheck == $expectedValue")
            else
              Fail(s"'is' condition failed: $booleanValueToCheck is not == $expectedValue")
          }.recover {
            case _ => Fail(s"'is' condition failed: Invalid boolean $expectedValue in criteria")
          }.get
          case other => other.asString.map { stringValueToCheck =>
            Support.regexFor(criteriaValue) match {
              case Some(regex) => regex.findFirstIn(stringValueToCheck) match {
                case None => Fail(s"'is' condition failed: $regex in $stringValueToCheck. ")
                case Some(_) => OK(s"'is' condition succeeded: $regex in $stringValueToCheck. ")
              }
              case None => Fail(s"'is' condition not defined (value is blank), failed. ")
            }
          } | Fail(s"'is' condition failed: $other is not comparable")
        }
        case None => OK(s"'is' condition not defined (value is blank), skipped. ")
      }
      case Some("isnot") => criteriaValue match {
        case Some(expectedValue) => valueToCheck match {
          case EventDataValueNumber(numericValueToCheck) => Try {
            if (numericValueToCheck !=  BigDecimal(expectedValue))
              OK(s"'isnot' condition succeeded: $numericValueToCheck != $expectedValue")
            else
              Fail(s"'isnot' condition failed: $numericValueToCheck is == $expectedValue")
          }.recover {
            case _ => Fail(s"'isnot' condition failed: Unparsable number $expectedValue in criteria")
          }.get
          case EventDataValueBoolean(booleanValueToCheck) => Try {
            if (booleanValueToCheck !=  expectedValue.toBoolean)
              OK(s"'isnot' condition succeeded: $booleanValueToCheck != $expectedValue")
            else
              Fail(s"'isnot' condition failed: $booleanValueToCheck is == $expectedValue")
          }.recover {
            case _ => Fail(s"'isnot' condition failed: Invalid boolean $expectedValue in criteria")
          }.get
          case other => other.asString.map { stringValueToCheck =>
            Support.regexFor(criteriaValue) match {
              case Some(regex) => regex.findFirstIn(stringValueToCheck) match {
                case Some(_) => Fail(s"'isnot' condition failed: $regex in $stringValueToCheck. ")
                case None => OK(s"'isnot' condition succeeded: $regex in $stringValueToCheck. ")
              }
              case None => Fail(s"'isnot' condition not defined (value is blank), failed. ")
            }
          } | Fail(s"'is' condition failed: $other is not comparable")
        }
        case None => Fail(s"'isnot' condition not defined (value is blank), failed. ")
      }
      case Some("isless") => criteriaValue match {
        case Some(expectedValue) => valueToCheck match {
          case EventDataValueNumber(numericValueToCheck) => Try {
            if (numericValueToCheck <  BigDecimal(expectedValue))
              OK(s"'isless' condition succeeded: $numericValueToCheck < $expectedValue")
            else
              Fail(s"'isless' condition failed: $numericValueToCheck is not < $expectedValue")
          }.recover {
            case _ => Fail(s"'isless' condition failed: Unparsable number $expectedValue in criteria")
          }.get
          case other => other.asString.map { stringValueToCheck =>
            if (stringValueToCheck < expectedValue)
              OK(s"'isless' condition succeeded: $stringValueToCheck < $expectedValue")
            else
              Fail(s"'isless' condition failed: $stringValueToCheck is not < $expectedValue")
          } | Fail(s"'isless' condition failed: $other is not comparable")
        }
        case None => Fail(s"'isless' condition not defined (value is blank), failed. ")
      }
      case Some("ismore") => criteriaValue match {
        case Some(expectedValue) => valueToCheck match {
          case EventDataValueNumber(numericValueToCheck) => Try {
            if (numericValueToCheck > BigDecimal(expectedValue))
              OK(s"'ismore' condition succeeded: $numericValueToCheck > $expectedValue")
            else
              Fail(s"'ismore' condition failed: $numericValueToCheck is not > $expectedValue")
          }.recover {
            case _ => Fail(s"'ismore' condition failed: Unparsable number $expectedValue in criteria")
          }.get
          case other => other.asString.map { stringValueToCheck =>
            if (stringValueToCheck > expectedValue)
              OK(s"'ismore' condition succeeded: $stringValueToCheck > $expectedValue")
            else
              Fail(s"'ismore' condition failed: $stringValueToCheck is not > $expectedValue")
          } | Fail(s"'ismore' condition failed: $other is not comparable")
        }
        case None => Fail(s"'ismore' condition not defined (value is blank), failed. ")
      }
      case x => Fail("Unsupported condition $x")
    }

  override def toString = s"Field $name cond: $criteriaCondition val: $criteriaValue"
}

private case class TagCondition(name: String, criteriaValue: Option[String], criteriaCondition: Option[String]) extends Condition {
  override def metFor(frame: EventFrame): CheckResult = {
    checkConditions(
      locateRawFieldValue(frame, "tags", Seq()).asSeq.map(_.map(_.asString |"").filter { x => x == name }))
  }
  def checkConditions(value: Option[Seq[String]]): CheckResult =
    criteriaCondition match {
      case None => OK("condition not defined. skipped")
      case Some("is") =>
        Support.regexFor(criteriaValue) match {
          case Some(regex) =>
            val exists = value.exists(_.exists(regex.findFirstIn(_) match {
              case None => false
              case Some(_) => true
            }))
            if (exists)
              OK(s"'is' condition succeeded: $regex in $value. ")
            else
              Fail(s"'is' condition failed: $regex in $value. ")
          case None => Fail(s"'is' condition not defined, failed. ")
        }
      case Some("isnot") =>
        Support.regexFor(criteriaValue) match {
          case Some(regex) =>
            val exists = value.exists(_.exists(regex.findFirstIn(_) match {
              case None => false
              case Some(_) => true
            }))
            if (!exists)
              OK(s"'isnot' condition succeeded: $regex in $value. ")
            else
              Fail(s"'isnot' condition failed: $regex in $value. ")
          case None => Fail(s"'isnot' condition not defined, failed. ")
        }
      case x => Fail("Unsupported condition $x")
    }

  override def toString = s"Tag $name $name cond: $criteriaCondition val: $criteriaValue"
}


object Condition {

  val neverTrue = new NeverTrueCondition()
  val alwaysTrue = new AlwaysTrueCondition()

  def apply(optConfig: Option[JsValue]): \/[Fail, Condition] =
    optConfig.flatMap(condition) | \/-(alwaysTrue)

  private def condition(config: JsValue): Option[\/[Fail, Condition]] =
    (config ~> 'class).map { conditionClass =>
      for (
        builder <- conditionClass.toLowerCase match {
          case "any" => \/-(any(_))
          case "all" => \/-(all(_))
          case "tag" => \/-(tag(_))
          case "field" => \/-(field(_))
          case x => Fail(s"Invalid condition configuration, invalid condition class $x")
        };
        condition <- builder(config)
      ) yield condition
    }


  private def tag(config: JsValue): \/[Fail, Condition] =
    for (
      name <- config ~> 'name orFail s"Invalid tag config. Missing 'name' value. Contents: ${Json.stringify(config)}";
      v = config ~> 'value ;
      c = config ~> 'cond
    ) yield TagCondition(name, v, c)


  private def field(config: JsValue): \/[Fail, Condition] =
    for (
      name <- config ~> 'name orFail s"Invalid field config. Missing 'name' value. Contents: ${Json.stringify(config)}";
      v = config ~> 'value ;
      c = config ~> 'cond
    ) yield FieldCondition(name, v, c)


  private def conditionSequence(seq: Seq[JsValue]): \/[Fail, Seq[Condition]] =
    seq.foldLeft(Seq[Condition]().right[Fail]) { (result, nextConfig) =>
      for (
        currentSequence <- result;
        nextCondition <- Condition(Some(nextConfig))
      ) yield currentSequence :+ nextCondition
    }

  private def any(config: JsValue): \/[Fail, Condition] =
    for (
      seq <- config ##> 'list orFail s"Invalid 'any' condition - missing 'list' branch. Contents: ${Json.stringify(config)}";
      conditions <- conditionSequence(seq)
    ) yield AnyCondition(conditions)

  private def all(config: JsValue): \/[Fail, Condition] =
    for (
      seq <- config ##> 'list orFail s"Invalid 'all' condition - missing 'list' branch. Contents: ${Json.stringify(config)}";
      conditions <- conditionSequence(seq)
    ) yield AllCondition(conditions)


}



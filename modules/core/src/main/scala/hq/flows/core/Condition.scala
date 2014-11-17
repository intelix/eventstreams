/*
 * Copyright 2014 Intelix Pty Ltd
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

package hq.flows.core

import agent.controller.flow.Tools._
import com.typesafe.scalalogging.StrictLogging
import common.ToolExt.configHelper
import common.{JsonFrame, Fail, OK}
import play.api.libs.json.{JsArray, JsString, JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


sealed trait Condition extends StrictLogging {
  type CheckResult = \/[Fail,OK]
  def metFor(frame: JsonFrame): CheckResult
}


object Condition {


  def apply(optConfig: Option[JsValue]): \/[Fail, Condition] =
    optConfig.map(condition) | alwaysTrue

  private def condition(config: JsValue): \/[Fail, Condition] =
    for (
      conditionClass <- config ~> 'class \/> Fail(s"Invalid condition configuration, missing 'class' value. Contents: ${Json.stringify(config)}");
      builder <- conditionClass.toLowerCase match {
        case "any" => \/-(any(_))
        case "all" => \/-(all(_))
        case "tag" => \/-(tag(_))
        case "field" => \/-(field(_))
        case x => -\/(Fail(s"Invalid condition configuration, invalid condition class $x"))
      };
      condition <- builder(config)
    ) yield condition


  private def alwaysTrue = \/-(AlwaysTrueCondition())

  private def tag(config: JsValue): \/[Fail, Condition] =
    for (
      props <- config #> 'props \/> Fail(s"Invalid 'tag' condition - missing 'props' branch. Contents: ${Json.stringify(config)}");
      name <- props ~> 'name \/> Fail(s"Invalid tag config. Missing 'name' value. Contents: ${Json.stringify(props)}");
      is = props ~> 'is map (new Regex(_));
      isnot = props ~> 'isnot map (new Regex(_))
    ) yield TagCondition(name, is, isnot)


  private def field(config: JsValue): \/[Fail, Condition] =
    for (
      props <- config #> 'props \/> Fail(s"Invalid 'field' condition - missing 'props' branch. Contents: ${Json.stringify(config)}");
      name <- props ~> 'name \/> Fail(s"Invalid field config. Missing 'name' value. Contents: ${Json.stringify(props)}");
      is = props ~> 'is map (new Regex(_));
      isnot = props ~> 'isnot map (new Regex(_))
    ) yield FieldCondition(name, is, isnot)


  private def conditionSequence(seq: Seq[JsValue]): \/[Fail, Seq[Condition]] =
    seq.foldLeft(Seq[Condition]().right[Fail]) { (result, nextConfig) =>
      for (
        currentSequence <- result;
        nextCondition <- Condition(Some(nextConfig))
      ) yield currentSequence :+ nextCondition
    }

  private def any(config: JsValue): \/[Fail, Condition] =
    for (
      seq <- config ##> 'list \/> Fail(s"Invalid 'any' condition - missing 'list' branch. Contents: ${Json.stringify(config)}");
      conditions <- conditionSequence(seq)
    ) yield AnyCondition(conditions)

  private def all(config: JsValue): \/[Fail, Condition] =
    for (
      seq <- config ##> 'list \/> Fail(s"Invalid 'all' condition - missing 'list' branch. Contents: ${Json.stringify(config)}");
      conditions <- conditionSequence(seq)
    ) yield AllCondition(conditions)


}

private case class AlwaysTrueCondition() extends Condition {
  override def metFor(frame: JsonFrame): CheckResult = OK("always true condition").right
}


private case class AnyCondition(conditions: Seq[Condition]) extends Condition {
  override def metFor(frame: JsonFrame): CheckResult =
    conditions.map(_.metFor(frame)).collectFirst {
      case c if c.isRight => c
    } | Fail(s"All conditions failed for $frame").left
}

private case class AllCondition(conditions: Seq[Condition]) extends Condition {
  override def metFor(frame: JsonFrame): CheckResult =
    conditions.map(_.metFor(frame)).collectFirst {
      case c if c.isLeft => c
    } | OK().right
}

private case class FieldCondition(name: String, is: Option[Regex], isnot: Option[Regex]) extends Condition {
  override def metFor(frame: JsonFrame): CheckResult =
    checkConditions(
      locateFieldValue(
        frame, macroReplacement(frame, JsString(name))))

  def checkConditions(value: JsValue): CheckResult =
    for (
      isMet <- is match {
        case Some(regex) => regex.findFirstIn(value) match {
          case None => Fail(s"'is' condition failed: $regex in $value. ").left
          case Some(_) => OK(s"'is' condition succeeded: $regex in $value. ").right
        }
        case None => OK(s"'is' condition not defined, skipped. ").right
      };
      isNotMet <- isnot match {
        case Some(regex) => regex.findFirstIn(value) match {
          case Some(_) => Fail(s"'isnot' condition failed: $regex in $value. ").left
          case None => OK(s"'isnot' condition succeeded: $regex in $value. ").right
        }
        case None => OK(s"'isnot' condition not defined, skipped. ").right
      }
    ) yield isMet + isNotMet

  override def toString = s"Field $name is $is and isnot $isnot"
}


private case class TagCondition(name: String, is: Option[Regex], isnot: Option[Regex]) extends Condition {
  override def metFor(frame: JsonFrame): CheckResult = {
    checkConditions(
      locateFieldValue(frame, "tags").asOpt[JsArray].map(_.value.map(_.asOpt[String].getOrElse("")).filter(_ == name)))
  }

  def checkConditions(value: Option[Seq[String]]): CheckResult =
    for (
      isMet <- is match {
        case Some(regex) =>
          val exists = value.exists(_.exists(regex.findFirstIn(_) match {
            case None => false
            case Some(_) => true
          }))
          if (exists)
            OK(s"'is' condition succeeded: $regex in $value. ").right
          else
            Fail(s"'is' condition failed: $regex in $value. ").left
        case None => OK(s"'is' condition not defined, skipped. ").right
      };
      isNotMet <- isnot match {
        case Some(regex) =>
          val exists = value.exists(_.exists(regex.findFirstIn(_) match {
            case None => false
            case Some(_) => true
          }))
          if (!exists)
            OK(s"'isnot' condition succeeded: $regex in $value. ").right
          else
            Fail(s"'isnot' condition failed: $regex in $value. ").left
        case None => OK(s"'isnot' condition not defined, skipped. ").right
      }
    ) yield isMet + isNotMet

  override def toString = s"Tag $name is $is and isnot $isnot"
}



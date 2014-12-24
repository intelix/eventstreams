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

package eventstreams.core

import com.typesafe.scalalogging.StrictLogging
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.annotation.tailrec
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._

object Tools extends StrictLogging {


  implicit def configHelper(config: JsValue): ConfigExtOps = ConfigExtOps(config)

  implicit def configHelper(config: Option[JsValue]): ConfigExtOps = ConfigExtOps(config | Json.obj())


  case class ConfigExtOps(config: JsValue) {

    def #>(key: String) = (config \ key).asOpt[JsValue]

    def #>(key: Symbol) = (config \ key.name).asOpt[JsValue]

    def ~>(key: String) = (config \ key).asOpt[String] match {
      case Some(s) if !s.trim.isEmpty => Some(s)
      case _ => None
    }

    def ~>(key: Symbol) = (config \ key.name).asOpt[String] match {
      case Some(s) if !s.trim.isEmpty => Some(s)
      case _ => None
    }

    def ~*>(key: String) = (config \ key).asOpt[String]

    def ~*>(key: Symbol) = (config \ key.name).asOpt[String]


    def +>(key: String) = (config \ key).asOpt[Int]

    def +>(key: Symbol) = (config \ key.name).asOpt[Int]

    def +&>(key: String) = (config \ key).asOpt[Double]

    def +&>(key: Symbol) = (config \ key.name).asOpt[Double]

    def ++>(key: String) = (config \ key).asOpt[Long]

    def ++>(key: Symbol) = (config \ key.name).asOpt[Long]

    def ?>(key: String) = (config \ key).asOpt[Boolean]

    def ?>(key: Symbol) = (config \ key.name).asOpt[Boolean]

    def ##>(key: String) = (config \ key).asOpt[JsArray].map(_.value)

    def ##>(key: Symbol) = (config \ key.name).asOpt[JsArray].map(_.value)

  }


  private val arrayPath = "^([^(]+)[(]([\\d\\s]+)[)]".r
  private val arrayMatch = "^a(.)$".r
  private val singleTypeMatch = "^(.)$".r
  private val macroMatch = ".*[$][{]([^}]+)[}].*".r

  private val dateMatch = ".*[$][{]now:([^}]+)[}].*".r
  private val eventDateMatch = ".*[$][{]eventts:([^}]+)[}].*".r

  implicit def s2b(s: String): Boolean = s.toBoolean

  implicit def s2n(s: String): BigDecimal = BigDecimal(s)

  implicit def jv2b(v: JsValue): Boolean = v match {
    case JsString(s) => s.toBoolean
    case JsNumber(n) => if (n.toInt == 1) true else false
    case JsBoolean(b) => b
    case JsArray(arr) => arr.seq.nonEmpty
    case JsNull => false
    case JsObject(x) => x.seq.nonEmpty
    case JsUndefined() => false
  }

  implicit def jv2n(v: JsValue): BigDecimal = v match {
    case JsString("") => BigDecimal(0)
    case JsString(s) => Try {
      BigDecimal(s)
    } match {
      case Success(x) => x
      case Failure(e) =>
        logger.info(s"Unable to convert $s to number")
        BigDecimal(0)
    }
    case JsNumber(n) => BigDecimal("" + n)
    case JsBoolean(b) => BigDecimal(if (b) 1 else 0)
    case JsArray(arr) => BigDecimal(0)
    case JsNull => BigDecimal(0)
    case JsObject(x) => BigDecimal(0)
    case JsUndefined() => BigDecimal(0)
  }

  implicit def jv2s(v: JsValue): String = v match {
    case JsString(s) => s
    case JsNumber(n) => n.toString()
    case JsBoolean(b) => b.toString
    case JsObject(b) => b.toString()
    case JsArray(arr) => arr.seq.mkString(",")
    case JsNull => ""
    case JsUndefined() => ""
  }

  def toPath(infixPath: String) = {
    val x = infixPath.split('.').flatMap(_.split("/"))
      .foldLeft[JsPath](__)((path, nextKey) => nextKey.trim match {
      case arrayPath(a, b) => (path \ a.trim)(b.trim.toIntExact)
      case value => path \ value
    })
    logger.debug("path of " + infixPath + " is " + x)
    x
  }

  def macroReplacement(frame: JsonFrame, v: String): String = macroReplacement(frame.event, frame.ctx, v)

  def macroReplacement(frame: JsonFrame, v: JsValue): JsValue = macroReplacement(frame.event, frame.ctx, v)

  def macroReplacement(json: JsValue, ctx: Map[String, JsValue], v: JsValue): JsValue = JsString(macroReplacement(json, ctx, v.asOpt[String].getOrElse("")))

  def macroReplacement(json: JsValue, ctx: Map[String, JsValue], v: String): String = {
    @tailrec
    def repl(value: String): String = {

      logger.debug("Trying to match " + value + " with " + macroMatch)

      value match {
        case eventDateMatch(s) =>
          logger.debug("Matched " + value + " with " + eventDateMatch + " to " + s)
          repl(value.replace("${eventts:" + s + "}", DateTimeFormat.forPattern(s).print(json ++> 'date_ts | java.lang.System.currentTimeMillis())))
        case dateMatch(s) =>
          logger.debug("Matched " + value + " with " + dateMatch + " to " + s)
          repl(value.replace("${now:" + s + "}", DateTimeFormat.forPattern(s).print(java.lang.System.currentTimeMillis())))
        case macroMatch(s) =>
          logger.debug("Matched " + value + " with " + macroMatch + " to " + s)
          repl(value.replace("${" + s + "}", locateFieldValue(json, ctx, s)))
        case _ => value
      }
    }
    repl(v)
  }

  def setValue(fieldType: String, s: JsValue, path: JsPath, json: JsValue): JsValue =
    fieldTypeConverter(fieldType) match {
      case arrayMatch(t) =>

        logger.debug("Setting " + s + " to " + path + " in " + json)

        json.set(path -> (json.getOpt(path) match {
          case Some(JsArray(arr)) => JsArray((arr :+ jsValueOfType(t)(s)).toSet.toSeq)
          case Some(x) => Json.arr(x, jsValueOfType(t)(s))
          case None => Json.arr(jsValueOfType(t)(s))
        }))
      case singleTypeMatch(t) => json.set(path -> jsValueOfType(t)(s))
      case x =>
        logger.error("Value doesn't match any type pattern " + x)
        Json.obj()
    }

  def templateToStringValue(frame: JsonFrame, template: String) =
    for (
      dirtyValue <- Tools.locateFieldValue(frame, Tools.macroReplacement(frame, template)) match {
        case JsString(s) => Some(s)
        case JsNumber(n) => Some(n.toString())
        case JsBoolean(b) => None
        case JsObject(b) => Some(b.toString())
        case JsArray(arr) => None
        case JsNull => None
        case JsUndefined() => None
      };
      cleanValue <- dirtyValue match {
        case x if x.trim.isEmpty => None
        case x => Some(x)
      }
    ) yield cleanValue


  def locateFieldValue(frame: JsonFrame, v: String): JsValue = locateFieldValue(frame.event, frame.ctx, v)

  def locateFieldValue(json: JsValue, ctx: Map[String, JsValue], v: String): JsValue =
    (json.getOpt(toPath(v)) getOrElse {
      ctx.getOrElse(v, JsString(""))
    }).asOpt[JsValue].getOrElse(JsString(""))

  def jsValueOfType(t: String)(value: JsValue): JsValue = {
    t match {
      case "s" => JsString(value)
      case "b" => JsBoolean(value)
      case "n" => JsNumber(value)
    }
  }

  private def fieldTypeConverter(fieldType: String): String = fieldType.toLowerCase match {
    case "string" => "s"
    case "number" => "n"
    case "boolean" => "b"
    case "string array" => "as"
    case "number array" => "an"
    case "boolean array" => "ab"
    case x => x
  }


}
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

import eventstreams.EventValuePathTools.{FieldSetter, FieldAccessor}
import EventValuePathTools._
import eventstreams.JSONTools._
import play.api.libs.json._

import scala.language.implicitConversions
import scala.util.Try
import scalaz.Scalaz._
import EventFrameConverter._

trait EventDataOp {
  this: EventData =>

  def asString: Option[String] = None

  def asNumber: Option[BigDecimal] = None

  def asBoolean: Option[Boolean] = None

  def asSeq: Option[Seq[EventData]] = Some(Seq(this))

  def asJson: JsValue

  def ~*>(s: Symbol): Option[String] = ~*>(s.name)

  def ++>(s: Symbol): Option[Long] = ++>(s.name)

  def +>(s: Symbol): Option[Int] = +>(s.name)

  def +&>(s: Symbol): Option[Double] = +&>(s.name)

  def ##>(s: Symbol): Option[Seq[EventData]] = ##>(s.name)

  def #>(s: Symbol): Option[EventData] = #>(s.name)

  def %>(s: Symbol): Option[EventFrame] = %>(s.name)

  def ?>(s: Symbol): Option[Boolean] = ?>(s.name)

  def ~*>(s: String): Option[String] = None

  def ~>(s: String) = ~*>(s) match {
    case Some(x) if !x.trim.isEmpty => Some(x)
    case _ => None
  }

  def ~>(s: Symbol): Option[String] = ~>(s.name)

  def ++>(s: String): Option[Long] = None

  def +>(s: String): Option[Int] = None

  def +&>(key: String): Option[Double] = None

  def ##>(s: String): Option[Seq[EventData]] = None

  def #>(s: String): Option[EventData] = None

  def %>(s: String): Option[EventFrame] = #>(s).flatMap {
    case x: EventFrame => Some(x)
    case x => None
  }

  def ?>(s: String): Option[Boolean] = None
}

sealed trait EventData extends EventDataOp

case class EventDataValueNil() extends EventData {
  override def asJson: JsValue = JsNull
  override def toString: String = "NIL"
  override def equals(obj: scala.Any): Boolean = obj match {
    case null => true
    case EventDataValueNil() => true
    case x => super.equals(obj)
  }

}

case class EventDataValueString(v: String) extends EventData {
  val value = Some(v)

  override def asJson: JsValue = JsString(v)

  override def asString: Option[String] = value

  override def asNumber: Option[BigDecimal] =
    try {
      Some(BigDecimal(v))
    } catch {
      case e: Exception => None
    }

  override def asBoolean: Option[Boolean] =
    try {
      Some(v.toBoolean)
    } catch {
      case e: Exception => None
    }

  override def toString: String = '"' + v + '"'

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: String => v.equals(x)
    case EventDataValueString(x) => v.equals(x)
    case x => super.equals(obj)
  }

  override def hashCode(): Int = v.hashCode()
}


case class EventDataValueNumber(v: BigDecimal) extends EventData {
  val value = Some(v)

  override def asJson: JsValue = JsNumber(v)

  override def asString: Option[String] = Some(v.toString())

  override def asNumber: Option[BigDecimal] = value

  override def asBoolean: Option[Boolean] = Some(v.intValue() != 0)
  override def toString: String = v.toString()

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: BigDecimal => v == x
    case x: Int => v.intValue() == x
    case x: Long => v.longValue() == x
    case x: Double => v.doubleValue() == x
    case x: Float => v.floatValue() == x
    case x: Short => v.shortValue() == x
    case x: Byte => v.byteValue() == x
    case EventDataValueNumber(x) => v.equals(x)
    case x => super.equals(obj)
  }
  override def hashCode(): Int = v.hashCode()

}

case class EventDataValueBoolean(v: Boolean) extends EventData {
  val value = Some(v)

  override def asJson: JsValue = JsBoolean(v)

  override def asString: Option[String] = Some(v.toString)

  override def asNumber: Option[BigDecimal] = Some(if (v) 1 else 0)

  override def asBoolean: Option[Boolean] = value

  override def toString: String = v.toString

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: Boolean => v.equals(x)
    case EventDataValueBoolean(x) => v.equals(x)
    case x => super.equals(obj)
  }
  override def hashCode(): Int = v.hashCode()

}

case class EventDataValueSeq(v: Seq[EventData]) extends EventData {
  val value = Some(v)

  override def asJson: JsValue = Json.toJson(v.map(_.asJson).toArray)

  override def asString: Option[String] = Some(v.mkString(","))

  override def asSeq: Option[Seq[EventData]] = value

  override def toString: String = v.mkString("[",",","]")

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: Seq[_] => v.equals(x)
    case EventDataValueSeq(x) => v.equals(x)
    case x => super.equals(obj)
  }
  override def hashCode(): Int = v.hashCode()

}

object EventFrameConverter {

  def fromJson(json: JsValue): EventFrame = json match {
    case x: JsObject => EventFrame(x.keys.map { key => key -> fromJsonToData(json #> key | Json.obj())}.toMap)
    case x => EventFrame()
  }

  private def fromJsonToData(json: JsValue): EventData = json match {
    case x: JsObject => EventFrame(x.keys.map { key => key -> fromJsonToData(x #> key | Json.obj()) }.toMap)
    case JsNumber(n) => EventDataValueNumber(n)
    case JsBoolean(b) => EventDataValueBoolean(b)
    case JsArray(arr) => EventDataValueSeq(arr.map(fromJsonToData))
    case JsNull => EventDataValueNil()
    case JsUndefined() => EventDataValueNil()
    case JsString(s) => EventDataValueString(s)
    
  }

  implicit def valueToEventData(v: String): EventData = wrap(v)
  implicit def valueToEventData(v: Long): EventData = wrap(v)
  implicit def valueToEventData(v: Int): EventData = wrap(v)
  implicit def valueToEventData(v: Boolean): EventData = wrap(v)
  implicit def valueToEventData(v: BigDecimal): EventData = wrap(v)
  implicit def optionsConverter(config: Option[EventDataOp]): EventDataOp = config | EventFrame()

  def wrap(v: Any): EventData = v match {
    case x: EventData => x
    case x: JsObject => EventFrame(x.keys.map { key => key -> wrap(x #> key | Json.obj()) }.toMap)
    case JsNumber(n) => wrap(n)
    case JsBoolean(b) => wrap(b)
    case JsArray(arr) => EventDataValueSeq(arr.map(fromJsonToData))
    case JsNull => EventDataValueNil()
    case JsUndefined() => EventDataValueNil()
    case JsString(s) => wrap(s)
    case v: String => EventDataValueString(v)
    case v: Int => EventDataValueNumber(v)
    case v: Long => EventDataValueNumber(v)
    case v: Double => EventDataValueNumber(v)
    case v: BigDecimal => EventDataValueNumber(v)
    case v: Boolean => EventDataValueBoolean(v)
    case v: Seq[_] => EventDataValueSeq(v.map(wrap))
    case v: Map[_,_] => EventFrame(v.map { case (a,b) => a.toString -> wrap(b) })
  }

}


object EventFrame {
  def apply(v: Tuple2[String, Any]*) = new EventFrame(v: _*)
}
case class EventFrame(v: Map[String, EventData]) extends EventData {
  def this(v: Tuple2[String, Any]*) = this(v.map { case (a,b) => a.toString -> EventFrameConverter.wrap(b) }.toMap)

  val naValue = "n/a"
  val value = Some(v)

  override def asJson: JsValue = Json.obj(v.map { case (a,b) => a -> Json.toJsFieldJsValueWrapper(b.asJson) }.toSeq: _*)

  override def ~*>(s: String): Option[String] = v.get(s).flatMap(_.asString)

  override def ++>(s: String): Option[Long] = v.get(s).flatMap(_.asNumber.map(_.toLong))

  override def +>(s: String): Option[Int] = v.get(s).flatMap(_.asNumber.map(_.toInt))

  override def +&>(s: String): Option[Double] = v.get(s).flatMap(_.asNumber.map(_.toDouble))

  override def ##>(s: String): Option[Seq[EventData]] = v.get(s).flatMap(_.asSeq)

  override def #>(s: String): Option[EventData] = v.get(s)

  override def ?>(s: String): Option[Boolean] = v.get(s).flatMap(_.asBoolean)

  def +(kv: Tuple2[Any, Any]) = set(
    kv._1 match {
      case x: String => x
      case x: Symbol => x.name
      case x => x.toString
    },
    wrap(kv._2)
  )

  def set(key: String, value: EventData) = EventFrame(v + (key -> value))

  def set(key: Symbol, value: EventData) = EventFrame(v + (key.name -> value))

  def +(key: String, value: EventData) = EventFrame(v + (key -> value))

  def +(key: Symbol, value: EventData) = EventFrame(v + (key.name -> value))

  def replaceAllExisting(key: String, value: EventData): EventFrame = EventFrame(v.map {
    case (otherK,otherV) if otherK == key => (otherK, value)
    case (otherK,otherV) => (otherK, otherV match {
      case x: EventFrame => x.replaceAllExisting(key, value)
      case x => x
    })
  }.toMap)

  def eventIdOr(missing: => String) = eventId | missing

  def eventIdOrNA = eventIdOr(naValue)

  def eventId = ~>('eventId)

}

private object EventValuePathTools {
  val arrayPath = "^([^(]+)[(]([\\d\\s]+)[)]".r
  type FieldAccessor = EventData => Option[EventData]
  type FieldSetter = (EventFrame, EventData) => EventFrame

  def simplePath(k: String) = (e: EventData) => e #> k

  def simpleSetter(k: String) = (e: EventFrame, v: EventData) => e match {
    case x: EventFrame => x.set(k, v)
    case x => x
  }
}


case class EventValuePath(infixPath: String) {

  private val accessor: FieldAccessor = infixPath match {
    case x if x.contains("/") || x.contains(".") =>
      x.split('/').flatMap(_.split('.'))
        .foldLeft[FieldAccessor]((e: EventData) => Some(e)) {
        case (combinedFunc, token) =>
          (x: EventData) =>
            for (
              branch <- combinedFunc(x);
              nextBranch <- token match {
                case arrayPath(a, b) => Try {
                  (branch ##> a.trim).map(_(b.trim.toIntExact))
                } getOrElse None
                case s => branch #> s.trim
              }
            ) yield nextBranch
      }
    case x => simplePath(x)
  }
  private val setter: Option[FieldSetter] = infixPath match {
    case x if x.contains("/") || x.contains(".") =>
      x.split('/').flatMap(_.split('.'))
        .foldRight[Option[FieldSetter]](None) {
        case (token, combinedFunc) =>
          val func: (EventFrame, EventData) => EventData = combinedFunc match {
            case Some(f) => f
            case None => (e: EventFrame, v: EventData) => v
          }
          Some((e: EventFrame, v: EventData) =>
            token match {
              case arrayPath(a, b) =>
                val sequence = e ##> a.trim | Seq()
                e.set(a.trim,
                  Try {
                    val idx = b.trim.toIntExact
                    if (sequence.length > idx) {
                      sequence(idx) match {
                        case y: EventFrame => EventDataValueSeq(sequence.updated(idx, func(y, v)))
                        case y => EventDataValueSeq(sequence)
                      }
                    } else EventDataValueSeq(sequence :+ func(EventFrame(), v))
                  } getOrElse EventDataValueSeq(Seq(func(EventFrame(), v))))
              case s => e.set(s, func(e %> s | EventFrame(), v))
            }
          )
      }
    case x => Some(simpleSetter(x))
  }

  def extractRaw(data: EventData): Option[EventData] = accessor(data)

  def extractFrame(data: EventData): Option[EventFrame] = accessor(data) match {
    case b@ Some(EventFrame(_)) => b.asInstanceOf[Some[EventFrame]]
    case x => None
  }

  def extractAsStringFrom(data: EventData): Option[String] = extractRaw(data).flatMap(_.asString)

  def extractAsSeqFrom(data: EventData): Option[Seq[EventData]] = extractRaw(data).flatMap(_.asSeq)

  def extractAsNumberFrom(data: EventData): Option[BigDecimal] = extractRaw(data).flatMap(_.asNumber)

  def extractAsIntFrom(data: EventData): Option[Int] = extractAsNumberFrom(data).map(_.toInt)

  def extractAsLongFrom(data: EventData): Option[Long] = extractAsNumberFrom(data).map(_.toLong)

  def extractAsBooleanFrom(data: EventData): Option[Boolean] = extractRaw(data).flatMap(_.asBoolean)

  def setValueInto(data: EventFrame, v: EventData): EventFrame = setter.map(_(data, v)) | data

  def setStringInto(data: EventFrame, v: String): EventFrame = setValueInto(data, wrap(v))

  def setLongInto(data: EventFrame, v: Long): EventFrame = setValueInto(data, wrap(v))

  def setIntInto(data: EventFrame, v: Int): EventFrame = setValueInto(data, wrap(v))

  def setBooleanInto(data: EventFrame, v: Boolean): EventFrame = setValueInto(data, wrap(v))

  def setSeqInto(data: EventFrame, v: Seq[EventData]): EventFrame = setValueInto(data, wrap(v))

  def setSeqOfStringsInto(data: EventFrame, v: Seq[String]): EventFrame = setSeqInto(data, v.map(wrap))

  def setSeqOfIntsInto(data: EventFrame, v: Seq[Int]): EventFrame = setSeqInto(data, v.map(wrap))

  def setSeqOfLongsInto(data: EventFrame, v: Seq[Long]): EventFrame = setSeqInto(data, v.map(wrap))

  def setSeqOfSeqInto(data: EventFrame, v: Seq[Seq[EventData]]): EventFrame = setSeqInto(data, v.map(wrap))

}
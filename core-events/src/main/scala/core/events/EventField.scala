package core.events

import scala.language.implicitConversions

trait EventField {
  def id: String
  def -->(v: Any) : EventFieldWithValue = new SimpleEventFieldWithValue(id, v)
  def is(v: Any) : EventFieldWithValue = new SimpleEventFieldWithValue(id, v)
//  def -->(v: => Any) : EventFieldWithValue = new SimpleEventFieldWithValue(id, v)
//  def is(v: => Any) : EventFieldWithValue = new SimpleEventFieldWithValue(id, v)

}


trait EventFieldWithValue {
  def fieldName: String
  def value: Any
}

//class SimpleEventFieldWithValue(val fieldName: String, v: => Any) extends EventFieldWithValue {
//  lazy val value = v
//
//  override def toString: String = fieldName + "=" + String.valueOf(value)
//}

class SimpleEventFieldWithValue(val fieldName: String, v: Any) extends EventFieldWithValue {
  val value = v

  override def toString: String = fieldName + "=" + String.valueOf(value)
}




case class SimpleField(id: String) extends EventField {
}
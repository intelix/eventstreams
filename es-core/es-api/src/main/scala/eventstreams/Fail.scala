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

import play.api.libs.json.JsValue
import scalaz._
import Scalaz._

trait Fail {
  def message: Option[String]
}

trait OK {
  def message: Option[String]
  def +(other: OK): OK
}

object Fail {
  def apply(cause: => String = "Generic failure", message: Option[String] = None) = new FailWithCause(cause, message).left
}

object OK {
  def apply() = Successful(None).right
  def apply(message: Option[String]) = new Successful(message).right
  def apply(details: => String, message: Option[String] = None) = new OKWithDetails(details, message).right
  def apply(payload: JsValue) = new OKWithPayload(payload).right
}




case class Successful(message: Option[String]) extends OK {
  override def +(other: OK): OK = new Successful(other.message)

  override def toString: String = "OK"
}

class OKWithDetails(arg: => String, val message: Option[String]) extends OK {
  lazy val details = arg

  override def +(other: OK): OK = other match {
    case x: Successful => this
    case x: OKWithDetails => new OKWithDetails(details + " and " + x.details, message)
    case x: OKWithPayload => new OKWithPayload(x.payload)
  }
  override def toString: String = "OK: " + arg
}

class OKWithPayload(arg: JsValue) extends OK {
  lazy val payload = arg

  override def message: Option[String] = None

  override def +(other: OK): OK = other match {
    case x: Successful => this
    case x: OKWithDetails => this
    case x: OKWithPayload => this
  }
  override def toString: String = "OK: " + arg
}

class FailWithCause(arg: => String, val message: Option[String]) extends Fail {
  lazy val cause = arg

  override def toString: String = "Fail: " + arg
}

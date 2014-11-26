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

package common

trait Fail {
  def message: Option[String]
}

trait OK {
  def message: Option[String]
  def +(other: OK): OK
}

object Fail {
  def apply(cause: => String = "Generic failure", message: Option[String] = None) = new FailWithCause(cause, message)
}

object OK {
  def apply() = new JustOK(None)
  def apply(message: Option[String]) = new JustOK(message)
  def apply(details: => String, message: Option[String] = None) = new OKWithDetails(details, message)
}

class JustOK(val message: Option[String]) extends OK {
  override def +(other: OK): OK = new JustOK(other.message)
}

class OKWithDetails(arg: => String, val message: Option[String]) extends OK {
  lazy val details = arg

  override def +(other: OK): OK = other match {
    case x: JustOK => this
    case x: OKWithDetails => OK(details + " and " + x.details)
  }
  override def toString: String = arg
}

class FailWithCause(arg: => String, val message: Option[String]) extends Fail {
  lazy val cause = arg

  override def toString: String = arg
}

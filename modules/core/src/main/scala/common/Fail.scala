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

trait Fail

trait OK {
  def +(other: OK): OK
}

object Fail {
  def apply(cause: => String) = new FailWithCause(cause)
}

object OK {
  def apply() = new JustOK()

  def apply(details: => String) = new OKWithDetails(details)
}

class JustOK() extends OK {
  override def +(other: OK): OK = new JustOK()
}

class OKWithDetails(arg: => String) extends OK {
  lazy val details = arg

  override def +(other: OK): OK = other match {
    case x: JustOK => this
    case x: OKWithDetails => OK(details + " and " + x.details)
  }
  override def toString: String = arg
}

class FailWithCause(arg: => String) extends Fail {
  lazy val cause = arg

  override def toString: String = arg
}

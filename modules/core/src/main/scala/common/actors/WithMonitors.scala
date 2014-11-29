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

package common.actors


trait WithMonitors {

  def withMonitor[T](list: (() => Unit)*)(v: T) = Monitored[T](sideEffectRunner(list))(v)

  private def sideEffectRunner[T](list: Seq[() => Unit])() = list.foreach(_())
}


trait Monitored[T] {

  val s: Long
  val v: T
  val sideEffect: () => Unit

  def move(v: T, s: Long): Monitored[T]

  def get: T = v

  def set(v: T): Monitored[T] = map(_ => v)

  def state: Long = s

  def map(f: T => T): Monitored[T] = flatMap { t =>
    val newOne = move(f(t), s + 1)
    sideEffect()
    newOne
  }

  def flatMap(f: T => Monitored[T]): Monitored[T] = f(v)

}


object Monitored {
  def apply[T](sideEffect: () => Unit)(v: T) = new MonitoredImpl[T](0, v, sideEffect)
}

class MonitoredImpl[T](val s: Long, val v: T, val sideEffect: () => Unit) extends Monitored[T] {
  override def move(v: T, s: Long): Monitored[T] = new MonitoredImpl[T](s, v, sideEffect)
}

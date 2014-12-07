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

import play.api.libs.json.{Json, JsArray, JsValue}

import scala.language.implicitConversions
import scalaz._
import Scalaz._

object ToolExt {

  implicit def configHelper(config: JsValue): ConfigExtOps = ConfigExtOps(config)
  implicit def configHelper(config: Option[JsValue]): ConfigExtOps = ConfigExtOps(config | Json.obj())


  case class ConfigExtOps(config: JsValue) {

    def #>(key: String) = (config \ key).asOpt[JsValue]

    def #>(key: Symbol) = (config \ key.name).asOpt[JsValue]

    def ~>(key: String) = (config \ key).asOpt[String] match {
      case Some(s) if !s.trim.isEmpty => Some(s)
      case _ => None
    }

    def ~>(key: Symbol) = (config \ key.name).asOpt[String]  match {
      case Some(s) if !s.trim.isEmpty => Some(s)
      case _ => None
    }

    def ~*>(key: String) = (config \ key).asOpt[String]

    def ~*>(key: Symbol) = (config \ key.name).asOpt[String]


    def +>(key: String) = (config \ key).asOpt[Int]

    def +>(key: Symbol) = (config \ key.name).asOpt[Int]

    def ++>(key: String) = (config \ key).asOpt[Long]

    def ++>(key: Symbol) = (config \ key.name).asOpt[Long]

    def ?>(key: String) = (config \ key).asOpt[Boolean]

    def ?>(key: Symbol) = (config \ key.name).asOpt[Boolean]

    def ##>(key: String) = (config \ key).asOpt[JsArray].map(_.value)

    def ##>(key: Symbol) = (config \ key.name).asOpt[JsArray].map(_.value)

  }

}

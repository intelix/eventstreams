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

import play.api._
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

object GlobalWeb extends GlobalSettings {


  // 404 - page not found error
  override def onHandlerNotFound(request: RequestHeader) = Future.successful(
    NotFound(views.html.web.errors.onHandlerNotFound(request))
  )

  // 500 - internal server error
  override def onError(request: RequestHeader, throwable: Throwable) = Future.successful(
    InternalServerError(views.html.web.errors.onError(throwable))
  )

  // called when a route is found, but it was not possible to bind the request parameters
  override def onBadRequest(request: RequestHeader, error: String) = Future.successful(
    BadRequest("Bad Request: " + error)
  )

}
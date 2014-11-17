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

package controllers.web

import actors.WebsocketActor
import akka.util.ByteString
import models._
import play.api._
import play.api.mvc._
import play.api.Play.current

object Application extends Controller {

	def index = Action { implicit request =>
		val computers = Computer.list
		Ok(views.html.web.index("Hello! I'm the WEB!", computers))
	}


	def hey = Action {
		Ok(views.html.web.rjs(""))
	}

	def socket = WebSocket.acceptWithActor[String, String] {
		req => actor =>
			WebsocketActor.props(actor)
	}



}
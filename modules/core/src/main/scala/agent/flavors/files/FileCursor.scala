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

package agent.flavors.files

import agent.core.Cursor
import play.api.libs.json.{Json, JsValue}


case class FileCursor(idx: ResourceIndex, positionWithinItem: Long) extends Cursor

object FileCursorTools {

  def fromJson(jsValue: Option[JsValue]): Option[Cursor] = {
    for (
      stateCfg <- jsValue;
      fileCursorCfg <- (stateCfg \ "fileCursor").asOpt[JsValue];
      seed <- (fileCursorCfg \ "idx" \ "seed").asOpt[Long];
      resourceId <- (fileCursorCfg \ "idx" \ "rId").asOpt[Long];
      positionWithinItem <- (fileCursorCfg \ "pos").asOpt[Long]
    ) yield {
      val state = FileCursor(ResourceIndex(seed, resourceId), positionWithinItem)
      state
    }
  }

  def toJson(c: Cursor): Option[JsValue] = c match {
    case FileCursor(ResourceIndex(seed, resourceId), positionWithinItem) =>
      Some(Json.obj(
        "fileCursor" -> Json.obj(
          "idx" -> Json.obj(
            "seed" -> seed,
            "rId" -> resourceId),
          "pos" -> positionWithinItem)))
    case _ => None
  }

}

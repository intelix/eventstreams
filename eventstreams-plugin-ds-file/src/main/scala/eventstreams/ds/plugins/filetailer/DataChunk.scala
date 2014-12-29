package eventstreams.ds.plugins.filetailer

import akka.util.ByteString
import eventstreams.core.agent.core.Cursor
import play.api.libs.json.JsValue

case class DataChunk(data: Option[ByteString], meta: Option[JsValue], cursor: Cursor, hasMore: Boolean)

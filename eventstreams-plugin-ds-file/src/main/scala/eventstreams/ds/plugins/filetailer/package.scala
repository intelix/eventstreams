package eventstreams.ds.plugins

import akka.util.ByteString
import eventstreams.core.agent.core.Cursor
import play.api.libs.json.JsValue

package object filetailer {

  case class DataChunk(data: Option[ByteString], meta: Option[JsValue], cursor: Cursor, hasMore: Boolean)
  case class ResourceIndex(seed: Long, resourceId: Long)
  case class FileResourceIdentificator(dir: String, name: String, createdTimestamp: Long, sizeNow: Long)  {
    def same(that: FileResourceIdentificator): Boolean = that match {
      case FileResourceIdentificator(thatDir, thatName, thatCreatedTs, thatSize) =>
        thatDir == dir && thatName == name && thatCreatedTs == createdTimestamp && thatSize >= sizeNow
      case _ => false
    }
  }
  case class IndexedEntity(idx: ResourceIndex, id: FileResourceIdentificator)
}

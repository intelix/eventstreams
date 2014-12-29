package eventstreams.ds.plugins.filetailer

import java.nio.CharBuffer

import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsValue, Json}


class OpenFileResource(idx: ResourceIndex,
                       id: FileResourceIdentificator,
                       handle: FileHandle) extends LazyLogging {

  private val ab = CharBuffer.allocate(1024 * 16).array()
  private var position: Long = 0
  private var atTail: Boolean = false

  def advanceTo(cursor: FileCursor): OpenFileResource = {
    val diff = cursor.positionWithinItem - position
    if (diff > 0) {
      skip(diff)
    }
    this
  }

  def nextChunk(): Option[ByteString] = {
    try {
      handle.reader.read(ab) match {
        case i if i < 1 =>
          atTail = true
          None
        case i =>
          atTail = i < ab.length
          position = position + i
          Some(ByteString(new String(ab, 0, i)))
      }
    } catch {
      case e: Exception =>
        logger.warn(s"Unable to read, id $id, idx $idx", e)
        None
    }
  }

  def cursor: FileCursor = FileCursor(idx, position)

  def moveToTail(): Unit = {
    skip(Long.MaxValue)
  }

  def atTheTail_? : Boolean = atTail

  def close(): Unit = {
    handle.close()
  }

  def canAdvanceTo(cursor: FileCursor): Boolean = {
    cursor.positionWithinItem >= position && cursor.idx == idx && exists_? && not_truncated_?
  }

  def getDetails(): Option[JsValue] = Some(Json.obj("datasource" -> Json.obj(
    "type" -> "file",
    "filename" -> handle.name,
    "directory" -> handle.folder,
    "fileTs" -> id.createdTimestamp,
    "fileId" -> (idx.seed + ":" + idx.resourceId)
  )))

  private def skip(entries: Long): Unit = {
    val skipped = try {
      handle.reader.skip(entries)
    } catch {
      case e: Exception =>
        logger.warn(s"Unable to skip, id $id, idx $idx", e)
        0
    }
    if (skipped < entries) {
      atTail = true
    }
    position = position + skipped
  }

  private def exists_? : Boolean = handle.exists && handle.isFile

  private def not_truncated_? : Boolean = handle.length >= Math.max(id.sizeNow, position)
}

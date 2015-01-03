package eventstreams.ds.plugins.filetailer

import java.nio.CharBuffer

import akka.util.ByteString
import core.events.EventOps.symbolToEventField
import core.events.{EventFieldWithValue, WithEventPublisher}
import play.api.libs.json.{JsValue, Json}


class OpenFileResource(val idx: ResourceIndex,
                       val id: FileResourceIdentificator,
                       val handle: FileHandle,
                       bufferSize: Int)
  extends FileTailerEvents with WithEventPublisher {

  private val ab = CharBuffer.allocate(bufferSize).array()
  private var position: Long = 0
  private var atTail: Boolean = false
  private var lastActivityTs = System.currentTimeMillis()


  override def commonFields: Seq[EventFieldWithValue] = super.commonFields ++ Seq('ResourceId --> idx, 'Name --> handle.name)

  def advanceTo(cursor: FileCursor): OpenFileResource = {
    val diff = cursor.positionWithinItem - position
    if (diff > 0) {
      SkippedTo >>('Position --> cursor.positionWithinItem, 'Skipped --> diff)
      skip(diff)
    }
    this
  }

  def nextChunk(): Option[ByteString] = {
    try {
      lastActivityTs = System.currentTimeMillis()
      handle.reader.read(ab) match {
        case i if i < 1 =>
          NoData >> ('Position --> position)
          atTail = true
          None
        case i =>
          atTail = i < ab.length
          NewDataBlock >>('BlockSize --> i, 'Position --> position, 'AtTail --> atTail)
          position = position + i
          Some(ByteString(new String(ab, 0, i)))
      }
    } catch {
      case e: Exception =>
        Error >>('Message --> "Unable to read", 'Error --> e.getMessage, 'Position --> position, 'AtTail --> atTail)
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

  def idlePeriodMs = System.currentTimeMillis() - lastActivityTs

  def canAdvanceTo(cursor: FileCursor): Boolean =
    cursor.positionWithinItem >= position &&
      (cursor.idx == idx) &&
      exists_? &&
      not_truncated_?


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
        Error >>('Message --> "Unable to skip", 'Error --> e.getMessage, 'Position --> position, 'AtTail --> atTail)
        0
    }
    if (skipped < entries) {
      atTail = true
    }
    position = position + skipped
  }

  private def exists_? : Boolean = handle.exists && handle.isFile

  private def not_truncated_? : Boolean = handle.length >= Math.max(id.sizeNow, position)

  override def toString: String = s"Resource $idx / $id position $position"
}

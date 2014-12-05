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

import java.io._
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.{GZIPInputStream, ZipInputStream}

import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.util.matching.Regex


trait Indexer {
  def startSession(flowId: String, target: MonitorTarget): IndexerSession
}

class FileIndexer extends Indexer {
  override def startSession(flowId: String, target: MonitorTarget): IndexerSession = target match {
    case t: RollingFileMonitorTarget => new FileIndexerSession(flowId, t, new H2ResourceCatalog(flowId, new File(t.directory)))
  }
}

trait OpenResource {
  def getDetails(): Option[JsValue]

  def advanceTo(cursor: FileCursor): OpenResource

  def canAdvanceTo(cursor: FileCursor): Boolean

  def moveToTail(): Unit

  def cursor: FileCursor

  def nextChunk(): Option[ByteString]

  def close(): Unit

  def atTheTail_? : Boolean
}

object OpenFileResource extends LazyLogging {

  def apply(idx: ResourceIndex, id: FileResourceIdentificator)(implicit charset: Charset = Charset.forName("UTF-8")): Option[OpenResource] = {

    val file = new File(id.dir + "/" + id.name)
    if (!file.exists() || !file.isFile) return None

    val stream = try {
      openStream(file)
    } catch {
      case e: Exception =>
        logger.error(s"Unable to initialise stream from $id, idx $idx", e)
        return None
    }

    val reader = try {
      new BufferedReader(new InputStreamReader(stream, charset))
    } catch {
      case e: Exception =>
        logger.error(s"Unable to initialise stream reader from $id, idx $idx", e)
        stream.close()
        return None
    }
    Some(new OpenFileResource(idx, id, file, reader))
  }

  private def openStream(file: File) = {
    logger.debug(s"Opening stream from $file")
    val rawStream = new FileInputStream(file)
    if (file.getName.endsWith("gz")) {
      new GZIPInputStream(rawStream)
    } else if (file.getName.endsWith("zip")) {
      new ZipInputStream(rawStream)
    } else {
      rawStream
    }
  }
}

class OpenFileResource(idx: ResourceIndex,
                       id: FileResourceIdentificator,
                       file: File,
                       reader: BufferedReader) extends OpenResource with LazyLogging {

  private val ab = CharBuffer.allocate(1024 * 64).array()
  private var position: Long = 0
  private var atTail: Boolean = false

  override def advanceTo(cursor: FileCursor): OpenResource = {
    val diff = cursor.positionWithinItem - position
    if (diff > 0) {
      skip(diff)
    }
    this
  }

  override def nextChunk(): Option[ByteString] = {
    try {
      reader.read(ab) match {
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

  override def cursor: FileCursor = FileCursor(idx, position)

  override def moveToTail(): Unit = {
    skip(Long.MaxValue)
  }

  override def atTheTail_? : Boolean = atTail

  override def close(): Unit = {
    reader.close()
  }

  override def canAdvanceTo(cursor: FileCursor): Boolean = {
    cursor.positionWithinItem >= position && cursor.idx == idx && exists_? && not_truncated_?
  }

  override def getDetails(): Option[JsValue] = Some(Json.obj("datasource" -> Json.obj(
    "type" -> "file",
    "filename" -> file.getName,
    "directory" -> file.getAbsolutePath,
    "fileTs" -> id.createdTimestamp,
    "fileId" -> (idx.seed + ":" + idx.resourceId)
  )))

  private def skip(entries: Long): Unit = {
    val skipped = try {
      reader.skip(entries)
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

  private def exists_? : Boolean = file.exists() && file.isFile

  private def not_truncated_? : Boolean = file.length() >= Math.max(id.sizeNow, position)
}

trait IndexerSession {
  def close(): Unit

  def tailCursor: Cursor

  def withOpenResource[T](c: Cursor)(f: OpenResource => Option[T]): Option[DataChunk[T, Cursor]]
}

case class FileIndexerSession(flowId: String, target: RollingFileMonitorTarget, catalog: ResourceCatalog) extends IndexerSession with LazyLogging {

  private val rolledFilePatternR = new Regex(target.rollingLogPattern)
  private val mainLogPatternR = new Regex(target.mainLogPattern)
  var currentSeed = System.currentTimeMillis()
  private var openResource: Option[OpenResource] = None

  def toFileCursor(cursor: Cursor): Option[FileCursor] = {
    cursor match {
      case f: FileCursor => Some(f)
      case NilCursor() => locateFirstResource().map(FileCursor(_, 0))
    }
  }

  override def tailCursor: Cursor = {
    {
      for (
        lastResource <- locateLastResource()
      ) yield FileCursor(lastResource, 0)
    } getOrElse NilCursor()
  }

  def closeIfRequired(resource: OpenResource): Unit = {
    if (resource.atTheTail_?) {
      closeOpenedResource()
    }
  }

  def advanceCursor(resource: OpenResource): Cursor = {
    if (resource.atTheTail_?)
      locateNextResource(resource.cursor.idx) match {
        case None => resource.cursor
        case Some(idx) => FileCursor(idx, 0)
      }
    else
      resource.cursor
  }

  override def withOpenResource[T](c: Cursor)(f: (OpenResource) => Option[T]): Option[DataChunk[T, Cursor]] = {
    openResourceAt(c) match {
      case Some(resource) =>
        try {
          val data = f(resource)
          val newCursor = advanceCursor(resource)
          Some(DataChunk(data, resource.getDetails(), newCursor, !resource.atTheTail_?))
        } catch {
          case x: Exception =>
            logger.error(s"Error during processing open resource $resource", x)
            None
        } finally {
          closeIfRequired(resource)
        }
      case None =>
        logger.debug(s"No resource can be opened at $c")
        None
    }
  }

  override def close(): Unit = {
    closeOpenedResource()
  }

  private def listOfAllFiles(pattern: Regex): List[FileResourceIdentificator] = {
    val minAcceptableFileSize = 16
    val dir = new File(target.directory)
    dir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = pattern.findFirstIn(name).isDefined
    })
      .filter(f => f.isFile && !f.getName.startsWith(".") && f.length() > minAcceptableFileSize)
      .sortWith({
      case (f1, f2) =>
        if (f1.lastModified() != f2.lastModified()) {
          f1.lastModified() < f2.lastModified()
        } else {
          f1.getName.compareTo(f2.getName) < 0
        }
    })
      .map { f =>
      val attributes = Files.readAttributes(f.toPath, classOf[BasicFileAttributes])
      FileResourceIdentificator(dir.getAbsolutePath, f.getName, attributes.creationTime.toMillis, f.length)
    }.toList
  }

  private def listOfFiles() = {
    val list = listOfAllFiles(rolledFilePatternR) ::: listOfAllFiles(mainLogPatternR)
    logger.debug(s"List of files: $list")
    list

  }

  private def updateMemory(indexToIdentificator: List[IndexedEntity]) = catalog.update(indexToIdentificator)

  private def updateCatalog(): Unit = {
    val list = listOfFiles()
    val result = list match {
      case Nil => List[IndexedEntity]()
      case head :: Nil => catalog.indexByResourceId(head) match {
        case Some(IndexedEntity(ResourceIndex(seed, 0), _)) => List(IndexedEntity(ResourceIndex(seed, 0), head))
        case Some(IndexedEntity(ResourceIndex(seed, _), _)) =>
          currentSeed = System.currentTimeMillis()
          List(IndexedEntity(ResourceIndex(currentSeed, 0), head))
        case None =>
          currentSeed = System.currentTimeMillis()
          List(IndexedEntity(ResourceIndex(currentSeed, 0), head))
      }
      case longList =>
        val startingPoint = catalog.indexByResourceId(longList.head) match {
          case Some(r) => r.idx
          case None =>
            ResourceIndex(currentSeed, 0)
        }
        val (_, resultingList) = longList.foldLeft((startingPoint, List[IndexedEntity]())) {
          case ((ResourceIndex(seed, rId), tempList), nextResourceId) =>
            (ResourceIndex(seed, rId + 1), tempList :+ IndexedEntity(ResourceIndex(seed, rId), nextResourceId))
        }
        resultingList
    }
    logger.debug(s"Index: $result")
    updateMemory(result)
  }

  private def locateLastResource(): Option[ResourceIndex] = {
    updateCatalog()
    catalog.last()
  }

  private def locateFirstResource(): Option[ResourceIndex] = {
    updateCatalog()
    catalog.head()
  }

  private def locateNextResource(index: ResourceIndex): Option[ResourceIndex] = {
    updateCatalog()
    catalog.nextAfter(index)
  }

  private def reopen(fc: FileCursor): Option[OpenResource] = {

    @tailrec
    def attempt(count: Int): Option[OpenResource] = {
      if (count == 3) return None
      Thread.sleep(500)
      closeOpenedResource()
      updateCatalog()
      catalog.resourceIdByIdx(fc.idx).flatMap(entity => OpenFileResource(entity.idx, entity.id)) match {
        case None =>
          attempt(count + 1)
        case result => result
      }
    }

    logger.debug(s"Reopening $fc")

    openResource = attempt(0).map(_.advanceTo(fc))
    openResource
  }

  private def openResourceAt(c: Cursor): Option[OpenResource] = {
    openResource = toFileCursor(c).flatMap { fc =>
      openResource match {
        case None => reopen(fc)
        case Some(resource) if !resource.canAdvanceTo(fc) => reopen(fc)
        case Some(resource) => Some(resource.advanceTo(fc))
      }
    }
    logger.debug(s"Opened $openResource at $c")
    openResource
  }

  private def closeOpenedResource() {
    openResource.foreach { r =>
      r.close()
      logger.debug(s"Closed $r")
    }
    openResource = None
  }

  private def skipToEnd(c: Cursor): Option[Cursor] = {
    withOpenResource[Unit](c) { resource =>
      resource.moveToTail()
      Some((): Unit)
    } map (_.cursor)
  }

}



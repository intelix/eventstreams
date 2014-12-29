package eventstreams.ds.plugins.filetailer

import java.io._
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.{GZIPInputStream, ZipInputStream}

import core.events.EventOps.symbolToEventField
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents

import scala.util.Try
import scala.util.matching.Regex

case class FileMeta(folder: String, name: String, isFile: Boolean, length: Long, lastModified: Long, creationTime: Long)

trait FileHandle {
  def reader: BufferedReader

  def close(): Unit

  def exists: Boolean

  def folder: String

  def name: String

  def isFile: Boolean

  def length: Long
}

trait FileSystem {

  def listFiles(directory: String, namePattern: Regex): Seq[FileMeta]

  def open(idx: ResourceIndex, id: FileResourceIdentificator, charset: Charset): Option[FileHandle]

}


trait FileSystemEvents extends ComponentWithBaseEvents {
  override def componentId: String = "FileSystem.Disk"
}


class DiskFileSystem extends FileSystem with FileSystemEvents with WithEventPublisher {
  override def listFiles(directory: String, namePattern: Regex): Seq[FileMeta] = {
    val dir = new File(directory)
    if (dir.exists() && dir.isDirectory) {
      dir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = namePattern.findFirstIn(name).isDefined
      }).map { f =>
        val attributes = Files.readAttributes(f.toPath, classOf[BasicFileAttributes])
        FileMeta(dir.getAbsolutePath, f.getName, f.isFile, f.length(), f.lastModified(), attributes.creationTime().toMillis)
      }
    } else Seq()
  }

  override def open(idx: ResourceIndex, id: FileResourceIdentificator, charset: Charset): Option[FileHandle] = {
    val file = new File(id.dir + "/" + id.name)
    if (!file.exists() || !file.isFile) return None

    val stream = try {
      openStream(file)
    } catch {
      case e: Exception =>
        Error >>('Message --> s"Unable to initialise stream from $id, idx $idx", 'Error --> e.getMessage)
        return None
    }

    val r = try {
      new BufferedReader(new InputStreamReader(stream, charset))
    } catch {
      case e: Exception =>
        Error >>('Message --> s"Unable to initialise stream reader from $id, idx $idx", 'Error --> e.getMessage)
        stream.close()
        return None
    }

    Some(new FileHandle {
      override def isFile: Boolean = file.isFile

      override def folder: String = file.getAbsolutePath

      override def reader: BufferedReader = r

      override def length: Long = file.length()

      override def name: String = file.getName

      override def close(): Unit = Try {
        reader.close()
      }

      override def exists: Boolean = file.exists()
    })

  }

  private def openStream(file: File) = {
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

package eventstreams

import java.io.{BufferedWriter, FileInputStream, FileOutputStream, OutputStreamWriter}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.util.Try

trait TempFolder extends BeforeAndAfterEach with BeforeAndAfterAll with StrictLogging {
  this: org.scalatest.Suite =>

  case class OpenFile(f: java.io.File, charset: Charset = Charset.forName("UTF-8")) {
    var writer: Option[BufferedWriter] = None

    def openAppend() =
      writer = Some(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), charset)))
    def openNew() =
      writer = Some(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), charset)))

    def close() = {
      writer.foreach{ w => Try {w.close()} }
      writer = None
    }

    def write(s: String) = writer.foreach { w =>
      w.write(s)
      w.flush()
    }

    def rollGz(name: String) =  {
      close()
      val is = new FileInputStream(f)
      val os = new FileOutputStream(tempDirPath + "/" + name)
      val gz = new GZIPOutputStream(os)
      val buffer = ByteBuffer.allocate(1024 * 16).array()
      var len = 0
      do {
        len = is.read(buffer)
        if (len > -1) {
          gz.write(buffer,0, len)
        }
      } while (len > -1)
      gz.close()
      os.close()
      is.close()
      f.delete()
      f.createNewFile()
      openNew()
    }

    openNew()
  }

  override def afterEach(): Unit = {
    Option(tempDir.listFiles).map(_.toList).getOrElse(Nil).foreach(_.delete)
    logger.debug(s"Cleaned files under ${tempDir.getPath}")
    super.afterEach()
  }


  override def afterAll(): Unit = {
    delete
    super.afterAll()
  }

  lazy val tempDir = {
    val dir = java.io.File.createTempFile("test", "")
    dir.delete
    logger.debug(s"Created $dir")
    dir.mkdir
    dir
  }

  def tempDirPath = tempDir.getPath

  def withNewFile(name: String)(f: OpenFile => Unit) = {
    val openFile = OpenFile(createNewFile(name))
    try {
      f(openFile)
    } finally {
      openFile.close()
    }
  }


  def createNewFile(name: String) = {
    val f = new java.io.File(tempDir.getPath+"/"+name)
    logger.debug(s"Created ${tempDir.getPath+"/"+name}")
    f.createNewFile
    f
  }

  def delete = {
    Option(tempDir.listFiles).map(_.toList).getOrElse(Nil).foreach(_.delete)
    tempDir.delete
    logger.debug(s"Cleaned files and directory ${tempDir.getPath}")
  }

}

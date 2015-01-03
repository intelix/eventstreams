package eventstreams.filetailer

import java.io._
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers}

trait TempFolder extends BeforeAndAfterEach with BeforeAndAfterAll with StrictLogging with Matchers {
  this: org.scalatest.Suite =>

  case class OpenFile(f: java.io.File, charset: Charset = Charset.forName("UTF-8"), append: Boolean = false) {
    var writer: Option[BufferedWriter] = None

    def openAppend() =
      writer = Some(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), charset)))
    def openNew() =
      writer = Some(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), charset)))

    def close() = {
      writer.foreach{ w => w.close() }
      writer = None
    }

    def write(s: String) = writer.foreach { w =>
      w.write(s)
      w.flush()
    }



    def delete(name: String) = deleteFile(new File(tempDirPath + "/" + name), false) shouldBe true

    private def deleteFile(f: File, truncateOnly: Boolean = true) = {
      def tryDelete(attempt: Int): Boolean = f.delete() match {
        case false if attempt < 25 =>
          logger.info("Failed to delete file " + f.getAbsolutePath +" after " + attempt + " attempt(s)")
          Thread.sleep(65)
          tryDelete(attempt + 1)
        case x => x
      }
      if (truncateOnly) {
        val s = new FileOutputStream(f)
        s.getChannel.truncate(0)
        logger.info("Size after truncate: " + s.getChannel.size())
        val result = s.getChannel.size() == 0
        s.close()
        result
      } else  tryDelete(1)
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
      writer.foreach(_.close())
      deleteFile(f) shouldBe true
//      f.createNewFile() shouldBe true
      openNew()
      write("")
      logger.info(s"$f rolled into ${tempDirPath + "/" + name}")
    }

    def rollOpen(name: String) =  {
      close()
      val is = new FileInputStream(f)
      val os = new FileOutputStream(tempDirPath + "/" + name)
      val buffer = ByteBuffer.allocate(1024 * 16).array()
      var len = 0
      do {
        len = is.read(buffer)
        if (len > -1) {
          os.write(buffer,0, len)
        }
      } while (len > -1)
      os.close()
      is.close()
      writer.foreach(_.close())
      deleteFile(f) shouldBe true
//      f.createNewFile() shouldBe true
      openNew()
      write("")
    }

    if (append) openAppend() else openNew()
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

  def withExistingFile(name: String)(f: OpenFile => Unit) = {
    val openFile = OpenFile(createNewFile(name), append = true)
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

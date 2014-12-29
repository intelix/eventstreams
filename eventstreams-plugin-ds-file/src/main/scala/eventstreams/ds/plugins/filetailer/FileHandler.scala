package eventstreams.ds.plugins.filetailer

import com.typesafe.scalalogging.StrictLogging
import eventstreams.core.actors.Stoppable
import eventstreams.core.agent.core.{Cursor, NilCursor}

import scalaz.Scalaz._


trait FileHandler extends Stoppable with StrictLogging {
  _: FileSystemComponent with ResourceCatalogComponent with MonitoringTarget =>

  private var openResource: Option[OpenFileResource] = None

  var currentCursor: Option[Cursor] = None

  def fileCursor = cursorToFileCursor(currentCursor | initialCursor)

  private def cursorToFileCursor(cursor: Cursor): Option[FileCursor] = {
    cursor match {
      case f: FileCursor => Some(f)
      case NilCursor() => locateFirstResource().map(FileCursor(_, 0))
    }
  }

  def initialCursor: Cursor = {
    for (
      lastResource <- initialPosition match {
        case StartWithFirst() => locateFirstResource()
        case StartWithLast() => locateLastResource()
      }
    ) yield FileCursor(lastResource, 0)
  } getOrElse NilCursor()


  /*
  def closeIfRequired(resource: OpenFileResource): Unit = {
    if (resource.atTheTail_?) {
      closeOpenedResource()
    }
  }
  */

  def advanceCursor(resource: OpenFileResource): Cursor = {
    if (resource.atTheTail_?)
      locateNextResource(resource.cursor.idx) match {
        case None => resource.cursor
        case Some(idx) => FileCursor(idx, 0)
      }
    else
      resource.cursor
  }


  override def stop(reason: Option[String]): Unit = {
    closeOpenedResource()
    super.stop(reason)
  }

  def reopen() = {
    closeOpenedResource()
    updateCatalog()
    for (
      fc <- fileCursor;
      r <- resourceCatalog.resourceIdByIdx(fc.idx);
      handle <- fileSystem.open(r.idx, r.id, charset)
    ) yield new OpenFileResource(r.idx, r.id, handle)
  }

  def openAtCursor() =
    for (
      fc <- fileCursor;
      resource <- openResource match {
        case Some(r) if r.canAdvanceTo(fc) =>
          Some(r.advanceTo(fc))
        case _ => reopen()
      }
    ) yield {
      openResource = Some(resource)
      resource
    }


  def pullNextChunk(): Option[DataChunk] =
    for (
      r <- openAtCursor();
      next <- r.nextChunk()
    ) yield DataChunk(Some(next), r.getDetails(), advanceCursor(r), !r.atTheTail_?)


  private def closeOpenedResource() {
    openResource.foreach { r =>
      r.close()
      logger.debug(s"Closed $r")
    }
    openResource = None
  }


}

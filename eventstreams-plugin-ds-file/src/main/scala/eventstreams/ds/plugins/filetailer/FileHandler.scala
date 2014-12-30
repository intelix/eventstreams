package eventstreams.ds.plugins.filetailer

import core.events.EventOps.symbolToEventField
import core.events.{EventFieldWithValue, WithEventPublisher}
import eventstreams.core.actors.{PipelineWithStatesActor, Stoppable}
import eventstreams.core.agent.core.{Cursor, NilCursor}

import scalaz.Scalaz._


trait FileHandler extends PipelineWithStatesActor with Stoppable with FileTailerEvents {
  _: FileSystemComponent with ResourceCatalogComponent with MonitoringTarget with WithEventPublisher  =>

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



  def closeIfRequired(): Unit = {
    openResource.foreach { r =>
      if (r.atTheTail_?) closeOpenedResource()
    }
  }


  override def becomePassive(): Unit = {
    closeOpenedResource()
    super.becomePassive()
  }




  def advanceCursor(resource: OpenFileResource): Cursor = {
    if (resource.atTheTail_?)
      locateNextResource(resource.cursor.idx) match {
        case Some(idx) if resourceIsCurrent(resource.idx, resource.id) => FileCursor(idx, 0)
        case _ => resource.cursor
      }
    else
      resource.cursor
  }


  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    closeOpenedResource()
    super.postStop()
  }

  override def stop(reason: Option[String]): Unit = {
    closeOpenedResource()
    super.stop(reason)
  }

  def reopen() = {
    closeOpenedResource()
    checkForFolderContentsChanges()
    for (
      fc <- fileCursor;
      r <- resourceCatalog.resourceIdByIdx(fc.idx);
      handle <- fileSystem.open(r.idx, r.id, charset);
      opened <-
        checkForFolderContentsChanges() match {
          case true =>
            handle.close()
            None
          case false =>
            Some(new OpenFileResource(r.idx, r.id, handle))
        }
    ) yield {
      if (opened.canAdvanceTo(fc)) {
        opened.advanceTo(fc)
      }
      Opened >>('Name --> r.id.name, 'ResourceId --> r.idx, 'Position --> opened.cursor.positionWithinItem)
      opened
    }
  }

  def openAtCursor() =
    for (
      fc <- fileCursor;
      resource <-
        openResource match {
          case Some(r) if r.atTheTail_? && checkForFolderContentsChanges() => reopen()
          case Some(r) if r.canAdvanceTo(fc) => Some(r.advanceTo(fc))
          case _ => reopen()
        }

    ) yield {
      openResource = Some(resource)
      resource
    }


  def pullNextChunk(): Option[DataChunk] =
    for (
      r <- openAtCursor()
    ) yield {
      val chunk = r.nextChunk()
      val cursor = advanceCursor(r)
      currentCursor = Some(cursor)
      closeIfRequired()
      DataChunk(chunk, r.getDetails(), cursor, !r.atTheTail_?)
    }


  private def closeOpenedResource() {
    openResource.foreach { r =>
      r.close()
      Closed >> ('Name --> r.handle.fullPath, 'ResourceId --> r.cursor.idx, 'AtTail --> r.atTheTail_?)
    }
    openResource = None
  }


}

package eventstreams.ds.plugins.filetailer

import com.typesafe.scalalogging.StrictLogging

import scala.util.matching.Regex
import scalaz.Scalaz._

trait InMemoryResourceCatalogComponent extends ResourceCatalogComponent {
  this: FileSystemComponent with MonitoringTarget =>
  override def resourceCatalog: ResourceCatalog = new InMemoryResourceCatalog()
}

trait ResourceCatalogComponent extends FileTailerConstants with StrictLogging {
  this: FileSystemComponent with MonitoringTarget =>

  def resourceCatalog: ResourceCatalog

  var currentSeed = java.lang.System.currentTimeMillis()

  def locateLastResource(): Option[ResourceIndex] = {
    updateCatalog()
    resourceCatalog.last()
  }

  def locateFirstResource(): Option[ResourceIndex] = {
    updateCatalog()
    resourceCatalog.head()
  }

  def locateNextResource(index: ResourceIndex): Option[ResourceIndex] = {
    updateCatalog()
    resourceCatalog.nextAfter(index)
  }

  private def listOfAllFiles(pattern: Regex): List[FileResourceIdentificator] = {
    val minAcceptableFileSize = 16
    fileSystem.listFiles(directory, pattern)
      .filter(f => f.isFile && !f.name.startsWith(".") && f.length > minAcceptableFileSize)
      .sortWith({
      case (f1, f2) => fileOrdering match {
        case OrderByLastModifiedAndName() => if (f1.lastModified != f2.lastModified) {
          f1.lastModified < f2.lastModified
        } else {
          f1.name.compareTo(f2.name) < 0
        }
        case OrderByNameOnly() => f1.name.compareTo(f2.name) < 0
      }
    }).map { f =>
      FileResourceIdentificator(f.folder, f.name, f.creationTime, f.length)
    }.toList
  }

  private def listOfFiles() = {
    val list = (rolledFilePatternR.map(listOfAllFiles) | List()) ::: listOfAllFiles(mainLogPatternR)
    logger.debug(s"List of files: $list")
    list

  }

  def updateCatalog(): Unit =
    resourceCatalog.update(listOfFiles() match {
      case Nil => List[IndexedEntity]()
      case head :: Nil => resourceCatalog.indexByResourceId(head) match {
        case Some(IndexedEntity(ResourceIndex(seed, 0), _)) => List(IndexedEntity(ResourceIndex(seed, 0), head))
        case Some(IndexedEntity(ResourceIndex(seed, _), _)) =>
          currentSeed = java.lang.System.currentTimeMillis()
          List(IndexedEntity(ResourceIndex(currentSeed, 0), head))
        case None =>
          currentSeed = java.lang.System.currentTimeMillis()
          List(IndexedEntity(ResourceIndex(currentSeed, 0), head))
      }
      case longList =>
        val startingPoint = resourceCatalog.indexByResourceId(longList.head) match {
          case Some(r) => r.idx
          case None =>
            ResourceIndex(currentSeed, 0)
        }
        val (_, resultingList) = longList.foldLeft((startingPoint, List[IndexedEntity]())) {
          case ((ResourceIndex(seed, rId), tempList), nextResourceId) =>
            (ResourceIndex(seed, rId + 1), tempList :+ IndexedEntity(ResourceIndex(seed, rId), nextResourceId))
        }
        resultingList
    })


}

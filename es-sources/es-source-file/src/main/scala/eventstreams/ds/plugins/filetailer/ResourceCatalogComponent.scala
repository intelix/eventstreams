package eventstreams.ds.plugins.filetailer

import core.events.WithEventPublisher
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors.ActorWithDataStore
import play.api.libs.json.{JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._

trait InMemoryResourceCatalogComponent extends ResourceCatalogComponent {
  this: FileSystemComponent with MonitoringTarget =>
  override val resourceCatalog: ResourceCatalog = new InMemoryResourceCatalog()
}

trait ResourceCatalogComponent
  extends FileTailerConstants
  with ActorWithDataStore
  with FileTailerEvents {
  this: FileSystemComponent with MonitoringTarget with WithEventPublisher =>

  def resourceCatalog: ResourceCatalog

  var currentSeed = java.lang.System.currentTimeMillis()
  var dataSnapshotReceived = false

  override def storageKey: Option[String] = Some(datasourceId + ":" + directory)

  @throws[Exception](classOf[Exception]) override
  def postStop(): Unit = {
    checkForFolderContentsChanges()
    storeData(configToJson(resourceCatalog.all))
    super.postStop()
  }

  private def configToJson(list: List[IndexedEntity]): JsValue =
    Json.obj("list" ->
      Json.toJson(list.map { ie =>
        Json.obj(
          "createdTimestamp" -> ie.id.createdTimestamp,
          "dir" -> ie.id.dir,
          "name" -> ie.id.name,
          "sizeNow" -> ie.id.sizeNow,
          "resourceId" -> ie.idx.resourceId,
          "seed" -> ie.idx.seed
        )
      }.toArray)
    )

  private def jsonToConfig(props: JsValue): List[IndexedEntity] =
    (props ##> 'list | Seq()).flatMap { v =>
      for (
        createdTimestamp <- v ++> 'createdTimestamp;
        dir <- v ~> 'dir;
        name <- v ~> 'name;
        sizeNow <- v ++> 'sizeNow;
        resourceId <- v ++> 'resourceId;
        seed <- v ++> 'seed
      ) yield IndexedEntity(ResourceIndex(seed, resourceId), FileResourceIdentificator(dir, name, createdTimestamp, sizeNow))
    }.toList


  override def applyData(key: String, data: JsValue): Unit = {
    dataSnapshotReceived = true
    resourceCatalog.update(jsonToConfig(data))
  }

  def locateLastResource(): Option[ResourceIndex] = {
    checkForFolderContentsChanges()
    resourceCatalog.last()
  }

  def locateFirstResource(): Option[ResourceIndex] = {
    checkForFolderContentsChanges()
    resourceCatalog.head()
  }

  def locateNextResource(index: ResourceIndex): Option[ResourceIndex] = {
    checkForFolderContentsChanges()
    resourceCatalog.nextAfter(index)
  }

  def resourceIsCurrent(index: ResourceIndex, id: FileResourceIdentificator) = resourceCatalog.resourceIdByIdx(index) match {
    case Some(IndexedEntity(idx, otherId)) if otherId == id =>
      true
    case _ => false
  }

  private def listOfAllFiles(pattern: Regex): List[FileResourceIdentificator] = {
    fileSystem.listFiles(directory, pattern)
      .filter(f => f.isFile && !f.name.startsWith("."))
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

  private def listOfFiles() =
    (rolledFilePatternR.map(listOfAllFiles) | List()) ::: listOfAllFiles(mainLogPatternR)

  def checkForFolderContentsChanges(): Boolean =
    dataSnapshotReceived && resourceCatalog.update(listOfFiles() match {
      case Nil => List[IndexedEntity]()
      case head :: Nil => resourceCatalog.indexByResourceId(head) match {
        case Some(IndexedEntity(ResourceIndex(seed, 0), _)) => List(IndexedEntity(ResourceIndex(seed, 0), head))
        case x@Some(IndexedEntity(ResourceIndex(seed, _), _)) =>
          currentSeed = java.lang.System.currentTimeMillis()
          ResourceCatalogNewSeed >>('Seed -> currentSeed, 'Reason -> "Matching entry recordId is not 0", 'Head -> head, 'Entry -> x)
          List(IndexedEntity(ResourceIndex(currentSeed, 0), head))
        case None =>
          currentSeed = java.lang.System.currentTimeMillis()
          ResourceCatalogNewSeed >>('Seed -> currentSeed, 'Reason -> "No entry matching current head", 'Head -> head, 'Entries -> resourceCatalog.all)
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

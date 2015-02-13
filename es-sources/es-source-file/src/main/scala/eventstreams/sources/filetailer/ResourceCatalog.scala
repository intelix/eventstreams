package eventstreams.sources.filetailer

import core.sysevents.WithSyseventPublisher

trait ResourceCatalog {
  def update(entities: List[IndexedEntity]): Boolean

  def indexByResourceId(identificator: FileResourceIdentificator): Option[IndexedEntity]

  def resourceIdByIdx(index: ResourceIndex): Option[IndexedEntity]

  def last(): Option[ResourceIndex]

  def head(): Option[ResourceIndex]

  def nextAfter(index: ResourceIndex): Option[ResourceIndex]

  def close(): Unit

  def all: List[IndexedEntity]
}


class InMemoryResourceCatalog extends ResourceCatalog with FileTailerSysevents with WithSyseventPublisher {
  var memory = List[IndexedEntity]()


  private def contentsForComparison(entities: List[IndexedEntity]) = entities.map(_.idx)

  override def update(entities: List[IndexedEntity]): Boolean =
    if (contentsForComparison(memory) == contentsForComparison(entities)) {
      memory = entities
      false
    } else {
      ResourceCatalogUpdate >> ('EntriesCount -> entities.size)
      memory = entities
      true
    }


  override def indexByResourceId(identificator: FileResourceIdentificator): Option[IndexedEntity] = {
    memory
      .find(_.id.same(identificator))
  }


  override def resourceIdByIdx(index: ResourceIndex): Option[IndexedEntity] = {
    memory
      .find(_.idx == index) orElse memory.headOption
  }

  override def last(): Option[ResourceIndex] =
    memory.lastOption.map(_.idx)



  override def head(): Option[ResourceIndex] =
    memory.headOption.map(_.idx)

  override def nextAfter(index: ResourceIndex): Option[ResourceIndex] =
    memory.dropWhile(_.idx != index) match {
      case current :: next :: _ => Some(next.idx)
      case _ => None
    }

  override def close(): Unit = {}

  override def all: List[IndexedEntity] = memory
}
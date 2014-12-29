package eventstreams.ds.plugins.filetailer

import com.typesafe.scalalogging.LazyLogging

trait ResourceCatalog {
  def update(entities: List[IndexedEntity]): Boolean

  def indexByResourceId(identificator: FileResourceIdentificator): Option[IndexedEntity]

  def resourceIdByIdx(index: ResourceIndex): Option[IndexedEntity]

  def last(): Option[ResourceIndex]

  def head(): Option[ResourceIndex]

  def nextAfter(index: ResourceIndex): Option[ResourceIndex]

  def close(): Unit
}


class InMemoryResourceCatalog extends ResourceCatalog with LazyLogging {
  var memory = List[IndexedEntity]()

  override def update(entities: List[IndexedEntity]): Boolean = {
    logger.debug(s"Catalog state: $entities")
    if (memory == entities)
      false
    else {
      memory = entities
      true
    }

  }

  override def indexByResourceId(identificator: FileResourceIdentificator): Option[IndexedEntity] = {
    memory
      .find(_.id.same(identificator))
  }


  override def resourceIdByIdx(index: ResourceIndex): Option[IndexedEntity] = {
    memory
      .find(_.idx == index) orElse memory.headOption
  }

  override def last(): Option[ResourceIndex] = {
    val result = memory.lastOption.map(_.idx)
    logger.debug(s"last resource = $result")
    result
  }

  override def head(): Option[ResourceIndex] = {
    val result = memory.headOption.map(_.idx)
    logger.debug(s"first resource = $result")
    result
  }

  override def nextAfter(index: ResourceIndex): Option[ResourceIndex] = {
    val result = memory.dropWhile(_.idx != index) match {
      case current :: next :: _ => Some(next.idx)
      case _ => None
    }
    logger.debug(s"next after $index = $result")
    result
  }

  override def close(): Unit = {}
}
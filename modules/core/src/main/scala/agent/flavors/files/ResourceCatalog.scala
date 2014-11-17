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

import java.io.File

import com.typesafe.scalalogging.LazyLogging

import scala.slick.jdbc.meta.MTable

trait ResourceCatalog {
  def update(entities: List[IndexedEntity]): Boolean
  def indexByResourceId(identificator: FileResourceIdentificator): Option[IndexedEntity]
  def resourceIdByIdx(index: ResourceIndex): Option[IndexedEntity]
  def last(): Option[ResourceIndex]
  def head(): Option[ResourceIndex]
  def nextAfter(index: ResourceIndex): Option[ResourceIndex]
  def close(): Unit
}

class H2ResourceCatalog(tapId: Long, dir: File, fileName: String = ".idx") extends InMemoryResourceCatalog {
  import scala.slick.driver.H2Driver.simple._

  private def dbURL(file: File): String = "jdbc:h2:" + file.getAbsolutePath + "/" + fileName

  private lazy val db = {
    require(dir.exists() && dir.isDirectory)
    val db = Database.forURL(dbURL(dir), driver = "org.h2.Driver");
    logger.info(s"Initialised db connection: $db at $dir")
    db withSession {
      implicit session =>
        if (MTable.getTables("findex").list.isEmpty) {
          logger.info("Creating findex table")
          findexes.ddl.create
        }
    }
    db
  }


  class FileIndex(tag: Tag) extends Table[(Long, Long, Long, String, String, Long, Long)](tag, "findex") {
    def * = (tapId, seed, resourceId, dir, name, createdTimestamp, sizeNow)

    def tapId = column[Long]("tapId")
    def seed = column[Long]("seed")
    def resourceId = column[Long]("resourceId")

    def dir = column[String]("dir")
    def name = column[String]("name")
    def createdTimestamp = column[Long]("createdTimestamp")
    def sizeNow = column[Long]("sizeNow")

  }

  private val findexes = TableQuery[FileIndex]

  load()

  private def load(): Unit = {
    val list = db withSession {
      implicit session =>
        (for {
          entity <- findexes if entity.tapId === tapId
        } yield entity).list
    }
    super.update(list.map {
      case (_, seed, resourceId, dirName, name, createdTimestamp, sizeNow) =>
        IndexedEntity(
          ResourceIndex(seed, resourceId),
          FileResourceIdentificator(dirName, name, createdTimestamp, sizeNow))
    })
  }


  private def persist(entities: List[IndexedEntity]) = {
    db withSession {
      implicit session =>
        (for {
          entity <- findexes if entity.tapId === tapId
        } yield entity).delete
        entities.foreach { entity =>
          findexes += (tapId, entity.idx.seed, entity.idx.resourceId, entity.id.dir, entity.id.name, entity.id.createdTimestamp, entity.id.sizeNow)
        }
        logger.debug(s"Persisted $entities")
    }
  }

  override def update(entities: List[IndexedEntity]): Boolean = {
    if (entities.map(_.idx) != memory.map(_.idx)) persist(entities)
    super.update(entities)
  }

  override def close() = {
    persist(memory)
    super.close()
  }
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

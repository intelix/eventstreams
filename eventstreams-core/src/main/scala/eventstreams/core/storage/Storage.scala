/*
 * Copyright 2014-15 Intelix Pty Ltd
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

package eventstreams.core.storage

import java.io.File

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.slick.jdbc.meta.MTable

trait Storage {

  def store(key: String, config: String, state: Option[String]): Unit

  def storeState(key: String, state: Option[String]): Unit

  def storeConfig(key: String, state: String): Unit

  def retrieve(key: String): Option[(String, Option[String])]

  def remove(key: String): Unit

  def retrieveAllMatching(key: String): List[(String, String, Option[String])]

}


object Storage extends StrictLogging {
  def apply(implicit config: Config): Storage = {
    val s: String = config.as[Option[String]]("eventstreams.storage.provider") getOrElse classOf[H2Storage].getName
    Class.forName(s).getDeclaredConstructor(classOf[Config]).newInstance(config).asInstanceOf[Storage]
  }
}


case class H2Storage(implicit config: Config) extends Storage with StrictLogging {

  import scala.slick.driver.H2Driver.simple._

  private lazy val db = {
    require(dir.exists() && dir.isDirectory)
    val db = Database.forURL(dbURL(dir), driver = "org.h2.Driver");
    db withSession {
      implicit session =>
        if (MTable.getTables("configurations").list.isEmpty) {
          configurations.ddl.create
        }
    }
    db
  }
  private val dir = new File(config.as[Option[String]]("eventstreams.storage.directory").getOrElse("."))

  override def store(key: String, config: String, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations.insertOrUpdate((key, config, state))
    }
  }

  load()

  override def retrieve(key: String): Option[(String, Option[String])] = {
    db withSession { implicit session =>
      (configurations filter (_.key === key)).firstOption
    } map {
      case (f, c, s) => (c, s)
    }
  }

  override def remove(key: String): Unit = {
    db withSession { implicit session =>
      (configurations filter (_.key === key)).delete
    }
  }

  override def storeState(key: String, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations
        .filter(_.key === key)
        .map(p => p.state)
        .update(state)
    }
  }

  override def storeConfig(key: String, config: String): Unit = {
    db withSession { implicit session =>
      configurations
        .filter(_.key === key)
        .map(p => p.conf)
        .update(config)
    }
  }

  override def retrieveAllMatching(key: String): List[(String, String, Option[String])] = {
    db withSession { implicit session =>
      (for {
        entry <- configurations if entry.key like (key + "%")
      } yield (entry.key, entry.conf, entry.state)).list
    }
  }

  private def dbURL(file: File): String =
    "jdbc:h2:" + dir.getAbsolutePath + "/" + config.as[Option[String]]("eventstreams.storage.db").getOrElse("config")

  private def load(): Unit = {
    db withSession { implicit session =>
    }
  }

  private def configurations = TableQuery[Configurations]

  class Configurations(tag: Tag) extends Table[(String, String, Option[String])](tag, "configurations") {
    def * = (key, conf, state)

    def key = column[String]("key", O.PrimaryKey)

    def conf = column[String]("conf")

    def state = column[Option[String]]("state")
  }

}

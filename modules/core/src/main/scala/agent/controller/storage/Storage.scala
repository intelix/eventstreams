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

package agent.controller.storage

import java.io.File

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.slick.jdbc.meta.MTable

/**
 * Created by maks on 22/09/2014.
 */
trait Storage {

  def store(tapId: Long, config: String, state: Option[String]) : Unit
  def storeState(tapId: Long, state: Option[String]) : Unit
  def storeConfig(tapId: Long, state: String) : Unit
  def retrieve(tapId: Long) : Option[(String, Option[String])]
  def retrieveAll() : List[(Long, String, Option[String])]

}


object Storage extends StrictLogging {
  def apply(implicit config: Config): Storage = {
    val s: String = config.as[Option[String]]("agent.storage.provider") getOrElse "nugget.agent.controller.storage.H2Storage"
    Class.forName(s).getDeclaredConstructor(classOf[Config]).newInstance(config).asInstanceOf[Storage]
  }
}


case class H2Storage(implicit config: Config) extends Storage with StrictLogging {

  import scala.slick.driver.H2Driver.simple._

  private val dir = new File(config.as[Option[String]]("agent.storage.directory").getOrElse("."))

  private def dbURL(file: File): String = "jdbc:h2:" + dir.getAbsolutePath + "/storage"

  private lazy val db = {
    require(dir.exists() && dir.isDirectory)
    val db = Database.forURL(dbURL(dir), driver = "org.h2.Driver");
    logger.info(s"Initialised db connection: $db at $dir")
    db withSession {
      implicit session =>
        if (MTable.getTables("configurations").list.isEmpty) {
          logger.info("Creating configurations table")
          configurations.ddl.create
        }
    }
    db
  }

  load()

  private def load(): Unit = {
    db withSession { implicit session =>
    }
  }

  class Configurations(tag: Tag) extends Table[(Long, String, Option[String])](tag, "configurations") {
    def * = (tapId, conf, state)

    def tapId = column[Long]("tapId", O.PrimaryKey)

    def conf = column[String]("conf")

    def state = column[Option[String]]("state")
  }

  private def configurations = TableQuery[Configurations]

  override def store(tapId: Long, config: String, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations.insertOrUpdate((tapId, config, state))
    }
  }

  override def retrieve(tapId: Long): Option[(String, Option[String])] = {
    db withSession { implicit session =>
      (configurations filter (_.tapId === tapId)).firstOption
    } map {
      case (f, c, s) => (c,s)
    }
  }

  override def storeState(tapId: Long, state: Option[String]): Unit = {
    db withSession { implicit session =>
      configurations
        .filter(_.tapId === tapId)
        .map(p => p.state)
        .update(state)
    }
  }

  override def storeConfig(tapId: Long, config: String): Unit = {
    db withSession { implicit session =>
      configurations
        .filter(_.tapId === tapId)
        .map(p => p.conf)
        .update(config)
    }
  }

  override def retrieveAll(): List[(Long, String, Option[String])] = {
    db withSession { implicit session =>
      configurations.list
    }
  }
}

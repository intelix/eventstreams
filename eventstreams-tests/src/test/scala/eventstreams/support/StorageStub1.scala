package eventstreams.support

import com.typesafe.config.Config
import eventstreams.core.storage.Storage
import eventstreams.support.StorageStub1.{StoredEntry, storage}

import scala.collection.mutable


object StorageStub1 {

  case class StoredEntry(config: String, state: Option[String])

  private val map = mutable.Map[String, StoredEntry]()

  def storage = map
  
  def clear() = storage.synchronized {
    storage.clear()
  }
  
}

class StorageStub1(implicit config: Config) extends Storage {

  override def store(key: String, config: String, state: Option[String]): Unit = storage.synchronized {
    storage += key -> StoredEntry(config, state)
  }

  override def retrieveAllMatching(key: String): List[(String, String, Option[String])] =
    storage.synchronized {
      storage.collect {
        case (k, StoredEntry(c, s)) if k.startsWith(key) => (k, c, s)
      }.toList
    }


  override def storeState(key: String, state: Option[String]): Unit = storage.synchronized {
    storage += key -> storage.getOrElse(key, StoredEntry("", None)).copy(state = state)
  }

  override def remove(key: String): Unit = storage.synchronized {
    storage -= key
  }

  override def retrieve(key: String): Option[(String, Option[String])] = storage.synchronized {
    storage.get(key).map { e =>
      (e.config, e.state)
    }
  }

  override def storeConfig(key: String, config: String): Unit = storage.synchronized {
    storage += key -> storage.getOrElse(key, StoredEntry("", None)).copy(config = config)
  }
}

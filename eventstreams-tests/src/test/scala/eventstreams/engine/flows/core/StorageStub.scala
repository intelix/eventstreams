package eventstreams.engine.flows.core

import com.typesafe.config.Config
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.storage.Storage
import play.api.libs.json.Json


trait StorageStubEvents
  extends ComponentWithBaseEvents
  with WithEventPublisher {
  val Retrieve = 'Retrieve.info

  override def componentId: String = "StorageStub"
}


class StorageStub(implicit config: Config) extends Storage with StorageStubEvents {

  override def store(key: String, config: String, state: Option[String]): Unit = {

  }

  override def retrieveAllMatching(key: String): List[(String, String, Option[String])] = {
    List()
  }

  override def storeState(key: String, state: Option[String]): Unit = {

  }

  override def remove(key: String): Unit = {

  }

  override def retrieve(key: String): Option[(String, Option[String])] = {

    val json = Json.obj(
      "name" -> "Name",
      "sourceGateName" -> "/user/gate",
      "pipeline" -> Json.arr(
        Json.obj("class"->"enrich","fieldToEnrich"->"abc","targetValueTemplate"->"${abc1}","targetType"->"s")
      )
    )

    Retrieve >> ('Key -> key)
    Some(Json.stringify(json), None)
  }

  override def storeConfig(key: String, state: String): Unit = {

  }
}

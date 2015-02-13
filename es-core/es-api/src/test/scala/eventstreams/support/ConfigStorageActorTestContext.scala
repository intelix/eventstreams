package eventstreams.support

import com.typesafe.config.ConfigFactory
import eventstreams.core.storage.ConfigStorageActor

trait ConfigStorageActorTestContext {

  def withConfigStorage(idx: Int, system: ActorSystemWrapper) = {
    val cfg = ConfigFactory.parseString( s"""
    eventstreams.storage.provider = "eventstreams.support.StorageStub"
    test.instanceId=$idx
                                         """)
    system.start(ConfigStorageActor.props(cfg), ConfigStorageActor.id)
  }

}

package eventstreams.agent.support

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import eventstreams.core.storage.ConfigStorageActor

trait ConfigStorageActorTestContext {

  def withConfigStorage1(system: ActorSystemWrapper) = {
    val cfg = ConfigFactory.parseString( """
    ehub.storage.provider = "eventstreams.support.StorageStub1"
                                         """)
    system.start(ConfigStorageActor.props(cfg), ConfigStorageActor.id)
  }

  def withConfigStorage2(system: ActorSystemWrapper) = {
    val cfg = ConfigFactory.parseString( """
    ehub.storage.provider = "eventstreams.support.StorageStub2"
                                         """)
    system.start(ConfigStorageActor.props(cfg), ConfigStorageActor.id)
  }

}

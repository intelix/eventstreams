package eventstreams.core.actors.ext

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

class EventstreamsEnvironmentImpl(config: Config) extends Extension {

  val NodeId: Option[String] = config.as[Option[String]]("eventstreams.node.name")

}

object EventstreamsEnvironment extends ExtensionId[EventstreamsEnvironmentImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): EventstreamsEnvironmentImpl = new EventstreamsEnvironmentImpl(system.settings.config)

  override def lookup() = EventstreamsEnvironment

  override def get(system: ActorSystem): EventstreamsEnvironmentImpl = super.get(system)
}
package eventstreams.agent.support

import akka.cluster.Cluster
import eventstreams.core.components.routing.MessageRouterActor

trait MessageRouterActorTestContext {

   def withMessageRouter(system: ActorSystemWrapper) = {
     implicit val cluster = Cluster(system.underlyingSystem)
     system.start(MessageRouterActor.props(cluster, system.config), MessageRouterActor.id)
   }


 }

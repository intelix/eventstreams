package eventstreams.support

import akka.cluster.Cluster
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.components.cluster.ClusterManagerActor

trait ClusterManagerActorTestContext extends DefaultTopicKeys {

  def startClusterManager(sys: ActorSystemWrapper, cluster: Cluster) =
    withClusterManager(sys, cluster)

  private def withClusterManager(system: ActorSystemWrapper, cluster: Cluster) = {
    system.start(ClusterManagerActor.props(cluster, system.config), ClusterManagerActor.id)
  }


}

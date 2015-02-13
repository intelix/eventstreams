package eventstreams.support

import akka.cluster.Cluster

trait ClusterTestContext {

  def withCluster[T](system: ActorSystemWrapper)(f: Cluster => T): T = {
    val cluster = Cluster(system.underlyingSystem)
    f(cluster)
  }

}

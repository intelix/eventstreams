package eventstreams.support

import akka.actor.ActorRef
import akka.cluster.Cluster
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{Command, ComponentKey, LocalSubj, TopicKey}
import play.api.libs.json.JsValue

trait MessageRouterActorTestContext extends DefaultTopicKeys {

  def startMessageRouter(system: ActorSystemWrapper, cluster: Cluster) =
    system.start(MessageRouterActor.props(cluster, system.config), MessageRouterActor.id)

  def sendCommand(system: ActorSystemWrapper, subject: Any, data: Option[JsValue]) =
    system.rootUserActorSelection(MessageRouterActor.id) ! Command(ActorRef.noSender, subject, None, data)

  def sendCommand(system: ActorSystemWrapper, localRoute: String, topic: TopicKey, data: Option[JsValue]) =
    system.rootUserActorSelection(MessageRouterActor.id) ! Command(ActorRef.noSender, LocalSubj(ComponentKey(localRoute), topic), None, data)

}

package eventstreams.support

import akka.actor.ActorRef
import akka.cluster.Cluster
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{ComponentKey, LocalSubj, TopicKey, Command}
import play.api.libs.json.JsValue

trait MessageRouterActorTestContext extends DefaultTopicKeys {

  var messageRouterActor: Option[ActorRef] = None

  def startMessageRouter(sys: ActorSystemWrapper, cluster: Cluster) =
    messageRouterActor = Some(withMessageRouter(sys, cluster))

  def stopMessageRouter() =
    messageRouterActor = None

  def sendCommand(subject: Any, data: Option[JsValue]) =
    messageRouterActor.foreach(_ ! Command(ActorRef.noSender, subject, None, data))

  def sendCommand(localRoute: String, topic: TopicKey, data: Option[JsValue]) =
    messageRouterActor.foreach(_ ! Command(ActorRef.noSender, LocalSubj(ComponentKey(localRoute), topic), None, data))

  def withMessageRouter(system: ActorSystemWrapper, cluster: Cluster) = {
    system.start(MessageRouterActor.props(cluster, system.config), MessageRouterActor.id)
  }


}

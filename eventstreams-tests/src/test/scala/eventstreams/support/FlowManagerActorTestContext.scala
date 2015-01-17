package eventstreams.support

import akka.actor.ActorRef
import akka.cluster.Cluster
import eventstreams.core.actors.DefaultTopicKeys
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.core.messages.{Command, ComponentKey, LocalSubj, TopicKey}
import eventstreams.engine.flows.FlowManagerActor
import play.api.libs.json.JsValue

trait FlowManagerActorTestContext extends DefaultTopicKeys {

  def startFlowManager(system: ActorSystemWrapper) =
    system.start(FlowManagerActor.props(system.config), FlowManagerActor.id)

  def flowManagerActorSelection(system: ActorSystemWrapper) = system.rootUserActorSelection(FlowManagerActor.id)
  
}

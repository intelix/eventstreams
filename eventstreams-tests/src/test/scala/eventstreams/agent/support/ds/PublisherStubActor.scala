package eventstreams.agent.support.ds

import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage.Request
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.JsonFrame
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors._
import eventstreams.core.agent.core.ProducedMessage
import play.api.libs.json.JsValue

trait PublisherStubActorEvents extends ComponentWithBaseEvents with StateChangeEvents with BaseActorEvents {

  val PublishingMessage = 'PublishingMessage.trace
  val NoDemandAtPublisher = 'NoDemandAtPublisher.trace
  val NewDemandAtPublisher = 'NewDemandAtPublisher.trace

  override def componentId: String = "Test.PublisherStubActor"
}

object PublisherStubActor extends PublisherStubActorEvents {
  def props = Props(new PublisherStubActor())
}

class PublisherStubActor
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[ProducedMessage]
  with PipelineWithStatesActor
  with PublisherStubActorEvents
  with WithEventPublisher {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def process(m: ProducedMessage) =
    if (totalDemand > 0) {
      PublishingMessage >> ('EventId -> m.value ~> 'eventId)
      onNext(m)
    } else {
      NoDemandAtPublisher >>()
    }

  def handler: Receive = {
    case m: ProducedMessage => process(m)
    case m: JsValue => process(ProducedMessage(m, None))
    case Request(n) => NewDemandAtPublisher >> ('Requested -> n)
  }

}

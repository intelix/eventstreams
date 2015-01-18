package eventstreams.support

import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage.Request
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors._
import eventstreams.core.agent.core.ProducedMessage
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._

trait PublisherStubActorEvents extends ComponentWithBaseEvents with StateChangeEvents with BaseActorEvents {

  val PublisherStubStarted = 'PublisherStubStarted.trace
  val MessageQueuedAtStub = 'MessageQueuedAtStub.trace
  val PublishingMessage = 'PublishingMessage.trace
  val NoDemandAtPublisher = 'NoDemandAtPublisher.trace
  val NewDemandAtPublisher = 'NewDemandAtPublisher.trace

  override def componentId: String = "Test.PublisherStubActor"
}

object PublisherStubActor extends PublisherStubActorEvents {
  def props(maybeState: Option[JsValue]) = Props(new PublisherStubActor(maybeState))
}

class PublisherStubActor(maybeState: Option[JsValue])
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[ProducedMessage]
  with PipelineWithStatesActor
  with PublisherStubActorEvents
  with WithEventPublisher
  with ActorWithTicks {

  private var replayList = List[ProducedMessage]()
  private val queue = mutable.Queue[ProducedMessage]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def preStart(): Unit = {
    super.preStart()
    PublisherStubStarted >> ('InitialState -> maybeState )
  }

  def process(m: ProducedMessage) = {
    MessageQueuedAtStub >> ('EventId -> m.value ~> 'eventId)
    replayList = replayList :+ m
    queue.enqueue(m)
    publishNext()
  }

  @tailrec
  final def publishNext(): Unit = {
    if (totalDemand > 0 && isActive && isComponentActive && queue.size > 0) {
      val m = queue.dequeue()
      PublishingMessage >> ('EventId -> m.value ~> 'eventId)
      onNext(m)
      publishNext()
    }
  }
  
  def handler: Receive = {
    case m: ProducedMessage => process(m)
    case m: JsValue => process(ProducedMessage(m, Some(Json.obj("id" -> (m ~> 'eventId | "na")))))
    case Request(n) =>
      NewDemandAtPublisher >> ('Requested -> n)
      publishNext()
  }


  override def becomeActive(): Unit = {
    super.becomeActive()
    publishNext()
  }

  override def internalProcessTick(): Unit = {
    publishNext()
    super.internalProcessTick()
  }
}

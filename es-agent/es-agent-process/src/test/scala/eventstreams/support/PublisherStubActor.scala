package eventstreams.support

import akka.actor.Props
import akka.stream.actor.ActorPublisherMessage.Request
import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.{ProducedMessage, JSONTools, EventFrame}
import JSONTools.configHelper
import eventstreams.core.actors._
import play.api.libs.json.{JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable
import scalaz.Scalaz._

trait PublisherStubActorSysevents extends ComponentWithBaseSysevents with StateChangeSysevents with BaseActorSysevents {

  val PublisherStubStarted = 'PublisherStubStarted.trace
  val MessageQueuedAtStub = 'MessageQueuedAtStub.trace
  val PublishingMessage = 'PublishingMessage.trace
  val NoDemandAtPublisher = 'NoDemandAtPublisher.trace
  val NewDemandAtPublisher = 'NewDemandAtPublisher.trace

  override def componentId: String = "Test.PublisherStubActor"
}

object PublisherStubActor extends PublisherStubActorSysevents {
  def props(maybeState: Option[JsValue]) = Props(new PublisherStubActor(maybeState))
}

class PublisherStubActor(maybeState: Option[JsValue])
  extends ActorWithComposableBehavior
  with StoppablePublisherActor[ProducedMessage]
  with PipelineWithStatesActor
  with PublisherStubActorSysevents
  with WithSyseventPublisher
  with ActorWithTicks {

  private var replayList = List[ProducedMessage]()
  private val queue = mutable.Queue[ProducedMessage]()

  override def commonBehavior: Receive = handler orElse super.commonBehavior


  override def preStart(): Unit = {
    super.preStart()
    PublisherStubStarted >> ('InitialState -> maybeState )
  }

  def process(m: ProducedMessage) = {
    MessageQueuedAtStub >> ('EventId -> m.value.eventIdOrNA)
    replayList = replayList :+ m
    queue.enqueue(m)
    publishNext()
  }

  @tailrec
  final def publishNext(): Unit = {
    if (totalDemand > 0 && isActive && isComponentActive && queue.size > 0) {
      val m = queue.dequeue()
      PublishingMessage >> ('EventId -> m.value.eventIdOrNA)
      onNext(m)
      publishNext()
    }
  }
  
  def handler: Receive = {
    case m: ProducedMessage => process(m)
//    case m: JsValue => process(ProducedMessage(m, Some(Json.obj("id" -> (m ~> 'eventId | "na")))))
    case m: EventFrame => process(ProducedMessage(m, Some(Json.obj("id" -> (m ~> 'eventId | "na")))))
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

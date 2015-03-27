package eventstreams.core.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch._
import com.typesafe.config.Config
import eventstreams.NowProvider
import eventstreams.core.metrics.Metrics._

import scala.annotation.tailrec

class InstrumentedMailbox  extends MailboxType with ProducesMessageQueue[InstrumentedNodeMessageQueue] {

  def this(settings: ActorSystem.Settings, config: Config) = this()

  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = new InstrumentedNodeMessageQueue(owner)

}

case class EnableInstrumentation(hostId: Option[String],systemId: Option[String],componentId: Option[String])

private class InstrumentationProxy(
                                    val sensorHostId: Option[String],
                                    val sensorSystemId: Option[String],
                                    val sensorComponentId: Option[String]) extends WithInstrumentationEnabled {
  import eventstreams.core.metrics.Metrics._

  val MailboxGroup = Some("Mailbox")

  lazy val WaitTimeTimerSensor = timerSensor(MailboxGroup, WaitTime)
  lazy val QueueDepthSensor = histogramSensor(MailboxGroup, QueueDepth)
  lazy val ArrivalRateSensor = meterSensor(MailboxGroup, ArrivalRate)
}

case class EnvelopeWithStamp(e: Envelope, enqueuedAt: Long)

class InstrumentedNodeMessageQueue(owner: Option[ActorRef]) extends AbstractNodeQueue[EnvelopeWithStamp] with MessageQueue with UnboundedMessageQueueSemantics {


  private var proxy: Option[InstrumentationProxy] = None

  final def enqueue(receiver: ActorRef, handle: Envelope): Unit =
    handle.message match {
      case EnableInstrumentation(h,s,c) =>
        proxy.foreach(_.destroySensors())
        proxy = Some(new InstrumentationProxy(h, s, c))
      case _ => 
        add(EnvelopeWithStamp(handle, System.nanoTime()))
        proxy.foreach{ p =>
          p.ArrivalRateSensor.update(1)
          p.QueueDepthSensor.update(count())
        }
    }

  final def dequeue(): Envelope = {
    val et = poll()
    if (et != null) {
      proxy.foreach { p =>
        p.WaitTimeTimerSensor.updateNs(System.nanoTime() - et.enqueuedAt)
        p.QueueDepthSensor.update(count())
      }
      et.e
    } else null
  }

  final def numberOfMessages: Int = count()

  final def hasMessages: Boolean = !isEmpty

  @tailrec final def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit = {
    proxy.foreach(_.destroySensors())
    proxy = None
    val envelope = dequeue()
    if (envelope ne null) {
      deadLetters.enqueue(owner, envelope)
      cleanUp(owner, deadLetters)
    }
  }
}
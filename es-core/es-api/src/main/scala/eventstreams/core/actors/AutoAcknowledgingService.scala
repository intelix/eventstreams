package eventstreams.core.actors

import eventstreams.{AcknowledgeAsProcessed, Acknowledgeable, Batch}

trait AutoAcknowledgingService[T] extends ActorWithComposableBehavior {

  override def commonBehavior: Receive = handler orElse super.commonBehavior

  def canAccept(count: Int): Boolean
  def onNext(e: T): Unit
  def onUnrecognised(e: Any): Unit = {}

  private def handler: Receive = {
    case m: Acknowledgeable[_] => m.msg match {
      case b: Batch[_] =>
        if (canAccept(b.entries.size)) {
          b.entries.foreach {
            case s => onNext(s.asInstanceOf[T])
          }
          sender() ! AcknowledgeAsProcessed(m.id)
        }
      case s => if (canAccept(1)) {
        onNext(s.asInstanceOf[T])
        sender() ! AcknowledgeAsProcessed(m.id)
      }
    }
  }
}

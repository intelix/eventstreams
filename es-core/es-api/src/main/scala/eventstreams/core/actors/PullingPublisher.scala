package eventstreams.core.actors

trait PullingPublisher[T] extends ActorWithTicks with StoppablePublisherActor[T] {

  def produceNext(maxCount: Int): Option[Seq[StreamElement]]

  override final def onRequestForMore(currentDemand: Int): Unit =
    produceNext(currentDemand) match {
      case Some(seq) => seq.foreach(pushToStream)
      case None => ()
    }


}

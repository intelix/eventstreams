package eventstreams.core.metrics

trait StatePublisher {

  def update(state: String)

}

package eventstreams


trait WithID[T] {
  def entityId: T
}

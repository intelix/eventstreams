package eventstreams

case class Batch[T](entries: Seq[T])

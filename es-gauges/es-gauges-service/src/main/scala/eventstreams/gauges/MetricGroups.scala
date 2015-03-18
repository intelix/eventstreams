package eventstreams.gauges

import scala.collection.SortedSet

trait GroupId {
  def id: String
}
trait GroupLike[T <: GroupId] extends GroupId {
  var items: SortedSet[T] = SortedSet[T]()(Ordering.by[T, String](_.id))
}

class MetricGroups {

}

class MetricLocationGroup {

}
class MetricLocation(val id: String) extends GroupLike[MetricSystem] {
}

class MetricSystem(val id: String) extends GroupLike[MetricComponent] {

}
class MetricComponent(val id: String) extends GroupLike[MetricName] {

}
class MetricName(val id: String) extends GroupId {

}

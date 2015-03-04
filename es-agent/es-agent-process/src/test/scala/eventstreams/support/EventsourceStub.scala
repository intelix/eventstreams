package eventstreams.support

import akka.actor.Props
import com.typesafe.scalalogging.StrictLogging
import eventstreams.Tools.configHelper
import eventstreams.{BuilderFromConfig, Fail, Tools}
import play.api.libs.json.JsValue

import scalaz.Scalaz._
import scalaz._

class EventsourceStub extends BuilderFromConfig[Props] with StrictLogging {
  override def configId: String = "stub"

  def build(props: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, Props] = {
    if (props ?> 'fail | false)
      Fail("on request").left
    else
      PublisherStubActor.props(maybeState).right
  }
}
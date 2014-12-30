package eventstreams.support

import akka.actor.Props
import eventstreams.core.{BuilderFromConfig, Fail}
import org.scalatest.Matchers
import play.api.libs.json.JsValue

import scalaz.{-\/, \/-}

trait BuilderFromConfigTestContext extends Matchers {
  def builder: BuilderFromConfig[Props]

  def config: JsValue

  def state: Option[JsValue] = None

  def id: Option[String] = None

  var instruction: Option[Props] = None

  def shouldNotBuild(f: Fail => Unit = _ => ()) = builder.build(config, state, id) match {
    case \/-(inst) => fail("Successfully built, but expected to fail: " + inst)
    case -\/(x) => f(x)
  }

  def shouldBuild(f: Props => Unit = _ => ()) = instruction match {
    case Some(inst) => f(inst)
    case None => builder.build(config, state, id) match {
      case \/-(inst) =>
        instruction = Some(inst)
        f(inst)
      case -\/(x) => fail("Failed with: " + x)
    }
  }
}

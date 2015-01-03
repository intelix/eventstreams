package eventstreams.support

import akka.actor.Props
import eventstreams.core.{BuilderFromConfig, Fail}
import org.scalatest.Matchers
import play.api.libs.json.JsValue

import scalaz.{-\/, \/-}

trait BuilderFromConfigTestContext extends Matchers {
  def builder: BuilderFromConfig[Props]

  def config: JsValue

  var state: Option[JsValue] = None

  def id: Option[String] = None

  var instance: Option[Props] = None

  def clearInstance() = instance = None
  
  def shouldNotBuild(f: Fail => Unit = _ => ()) = builder.build(config, state, id) match {
    case \/-(inst) => fail("Successfully built, but expected to fail: " + inst)
    case -\/(x) => f(x)
  }

  def shouldBuildNew(f: Props => Unit = _ => ()) = {
    clearInstance()
    shouldBuild(f)
  } 


  def shouldBuild(f: Props => Unit = _ => ()) = instance match {
    case Some(inst) => f(inst)
    case None => builder.build(config, state, id) match {
      case \/-(inst) =>
        instance = Some(inst)
        f(inst)
      case -\/(x) => fail("Failed with: " + x)
    }
  }
}

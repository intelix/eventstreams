package eventstreams.support

import _root_.core.events._
import com.typesafe.scalalogging.StrictLogging
import core.events.support.EventAssertions
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core.instructions.SimpleInstructionBuilder
import eventstreams.core.{Fail, EventFrame}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import play.api.libs.json.JsValue

import scalaz.{-\/, \/-}

trait TestHelpers extends FlatSpec with Matchers with EventAssertions with BeforeAndAfterEach with StrictLogging {

  trait WithSimpleInstructionBuilder {
    def builder: SimpleInstructionBuilder

    def config: JsValue
    
    var instruction: Option[SimpleInstructionType] = None

    def shouldNotBuild(f: Fail => Unit = _ => ()) = builder.simpleInstruction(config) match {
      case \/-(inst) => fail("Successfuly built, but expected to fail: " + inst)
      case -\/(x) => f(x)
    }

    def shouldBuild(f: SimpleInstructionType => Unit = _ => ()) = instruction match {
      case Some(inst) => f(inst)
      case None => builder.simpleInstruction(config) match {
        case \/-(inst) => 
          instruction = Some(inst)
          f(inst)
        case -\/(x) => fail("Failed with: " + x)
      }
    }

    def expectAny(json: EventFrame)(f: Seq[EventFrame] => Unit) =
      shouldBuild { i =>
        val result = i(json)
        f(result)
      }

    def expectN(json: EventFrame)(f: Seq[EventFrame] => Unit) =
      expectAny(json) { result =>
        f(result)
      }

    def expectOne(json: EventFrame)(f: EventFrame => Unit) =
      expectN(json) { result =>
        result should not be empty
        result should have size 1
        f(result(0))
      }

    def expectNone(json: EventFrame) =
      expectN(json) { result =>
        result should be (empty)
      }

    def expectEvent(json: EventFrame)(event: Event, values: FieldAndValue*) = {
      clearEvents()
      expectAny(json) { _ =>
        expectSomeEvents(event, values: _*)
      }
    }
    def expectNEvents(json: EventFrame)(count: Int, event: Event, values: FieldAndValue*) = {
      clearEvents()
      expectAny(json) { _ =>
        expectSomeEvents(count, event, values: _*)
      }
    }
  }


}

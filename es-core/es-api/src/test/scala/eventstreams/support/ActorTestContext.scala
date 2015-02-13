package eventstreams.support

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.scalalogging.StrictLogging
import core.sysevents.support.EventAssertions
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

class ActorTestContext(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll
  with EventAssertions with StrictLogging {


  override protected def beforeEach(): Unit = {
    LoggerFactory.getLogger("testseparator").debug("\n" * 3 + "-" * 120)
    super.beforeEach()
  }


  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

}

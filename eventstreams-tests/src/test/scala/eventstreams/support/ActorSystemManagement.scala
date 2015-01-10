package eventstreams.support

import org.scalatest.{BeforeAndAfterAll, Suite, BeforeAndAfterEach}

trait ActorSystemManagement

trait IsolatedActorSystems extends ActorSystemManagement with BeforeAndAfterEach {

  self: Suite with MultiActorSystemTestContext =>

  override protected def afterEach(): Unit = {
    destroyAllSystems()
    super.afterEach()
  }
}

trait SharedActorSystem extends ActorSystemManagement with BeforeAndAfterEach with BeforeAndAfterAll {

  self: Suite with MultiActorSystemTestContext =>

  override protected def afterAll(): Unit = {
    super.afterAll()
    destroyAllSystems()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    destroyAllActors()
  }

}

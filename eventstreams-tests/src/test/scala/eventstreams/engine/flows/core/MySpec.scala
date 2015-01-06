/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eventstreams.engine.flows.core

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import com.typesafe.config.ConfigFactory
import core.events.EventPublisherRef
import core.events.support.{EventAssertions, TestEventPublisher}
import eventstreams.core.BecomeActive
import eventstreams.core.storage.ConfigStorageActor
import eventstreams.engine.flows.{FlowActor, FlowActorEvents}
import eventstreams.plugins.essentials.EnrichInstructionEvents
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

object FlowActorTemplate {

  def start(id: String)(implicit system: ActorSystem): ActorRef = {
    implicit val config = ConfigFactory.load(java.lang.System.getProperty("config", "hq.conf"))
    val instructionsConfigsList = {
      val list = config.getConfigList("ehub.flows.instructions")
      (0 until list.size()).map(list.get).toList.sortBy[String](_.getString("name"))
    }
    val cfg = ConfigFactory.parseString( """
    ehub.storage.provider = "eventstreams.engine.flows.core.StorageStub"
                                         """)

    ConfigStorageActor.start(system, cfg)

    system.actorOf(FlowActor.props(id, instructionsConfigsList))

  }

}

class MySpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with EventAssertions {

  def this() = this(ActorSystem("MySpec"))


  def withFlowActor(id: String = "id")(f: (ActorRef) => Any): Unit = {
    val actor = FlowActorTemplate.start(id)
    try {
      f(actor)
    } finally {
      println("Stopping")
      system.stop(actor)
    }

  }


  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "An Echo actor" must {

    "send back messages unchanged" in {
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
    }

  }

  "FlowActor" must {

    "request configs" in withFlowActor(id = "id1") { flowActor =>

      object EnrichInstructionEvents extends EnrichInstructionEvents

      expectSomeEvents(EnrichInstructionEvents.Built, 'Field -> "abc", 'Type -> "s")

      EventPublisherRef.ref.asInstanceOf[TestEventPublisher].clear()

      flowActor ! BecomeActive()

      expectSomeEvents(FlowActorEvents.FlowStarted, 'ID -> "id1")


    }

  }


}
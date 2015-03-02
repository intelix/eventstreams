package eventstreams

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

import eventstreams.core.components.cluster.ClusterManagerActor._
import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.support.RouteeComponentStubOps.{componentKeyForRouteeStub1, componentKeyForRouteeStub2, routeeIdFor}
import eventstreams.support._
import org.scalatest.FlatSpec

trait MessageRouterTestContext
  extends FlatSpec with DummyNodeTestContext with SharedActorSystem {
 _: ActorSystemManagement =>

  trait WithThreeNodes extends WithDummyNode1 with WithDummyNode2 with WithDummyNode3 with RouteeComponentStub
  trait WithThreeNodesStarted extends WithThreeNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "dummy1,dummy2,dummy3", 'Node -> "dummy1")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "dummy1,dummy2,dummy3", 'Node -> "dummy2")
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "dummy1,dummy2,dummy3", 'Node -> "dummy3")
    clearEvents()
  }
  trait WithThreeNodesAndThreeComponents extends WithThreeNodesStarted {
    startRouteeComponentStub1(dummy1System)
    startRouteeComponentStub2(dummy1System)
    startRouteeComponentStub1(dummy3System)
    startMessageSubscriber1(dummy1System)
    startMessageSubscriber2(dummy1System)
    startMessageSubscriber1(dummy2System)
    expectExactlyNEvents(3, MessageRouterActor.RouteAdded)
    clearEvents()
  }


  trait WithOneSubscriber extends WithThreeNodesAndThreeComponents {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders)
    expectOneOrMoreEvents(MessageRouterActor.MessageForwarded)
    expectOneOrMoreEvents(MessageRouterActor.FirstSubjectSubscriber)
    clearEvents()
  }


  trait WithTwoSubscribersToInfo extends WithOneSubscriber {
    subscribeFrom2(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    clearEvents()    
  }

  trait WithThreeSubscribersToInfoAndOneToList extends WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_LIST))
    subscribeFrom1(dummy2System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectExactlyNEvents(2, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address)
    clearEvents()
  }



}
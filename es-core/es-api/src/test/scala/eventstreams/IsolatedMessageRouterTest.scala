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

import eventstreams.core.components.routing.MessageRouterActor
import eventstreams.support._

class IsolatedMessageRouterTest
  extends MessageRouterTestContext with IsolatedActorSystems {

  "... with two local one remote sub to info and one sub to list, message router" should s"resubscribe with restarted component on another node - eventually subscribe to providers" taggedAs OnlyThisTest in new WithThreeSubscribersToInfoAndOneToList {
    restartDummyNode1()
    expectSomeEventsWithTimeout(30000, 1, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
    duringPeriodInMillis(2000) {
      expectNoEvents(MessageRouterActor.NewSubscription)
    }
    expectExactlyNEvents(1, MessageRouterActor.NewSubjectSubscription)
    startRouteeComponentStub1(dummy1System)
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription)
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
  }



}
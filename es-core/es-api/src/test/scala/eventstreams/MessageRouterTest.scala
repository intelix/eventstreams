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
import eventstreams.support.RouteeComponentStubOps.{componentKeyForRouteeStub1, componentKeyForRouteeStub2, routeeIdFor}
import eventstreams.support._
import org.scalatest.FlatSpec
import eventstreams.core.components.cluster.ClusterManagerActor._

class MessageRouterTest
  extends FlatSpec with DummyNodeTestContext with SharedActorSystem {


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

  "Three nodes with message router on each, Message router" should "start" in new WithThreeNodes {
    expectSomeEventsWithTimeout(30000, ClusterStateChanged, 'Peers -> "dummy1,dummy2,dummy3", 'Node -> "dummy1")
    expectExactlyNEvents(3, MessageRouterActor.PreStart)
  }

  it should "register route to a new provider" in new WithThreeNodesStarted {
    startRouteeComponentStub1(dummy1System)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub1.key)
  }
  
  it should "register route to multiple new providers" in new WithThreeNodesStarted {
    startRouteeComponentStub1(dummy1System)
    startRouteeComponentStub2(dummy1System)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub1.key, 'InstanceAddress -> dummy1Address)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub2.key, 'InstanceAddress -> dummy1Address)
  }

  it should "register route to multiple new providers, instance on each node handling its own providers" in new WithThreeNodesStarted {
    startRouteeComponentStub1(dummy1System)
    startRouteeComponentStub2(dummy1System)
    startRouteeComponentStub1(dummy3System)
    expectExactlyNEvents(3, MessageRouterActor.RouteAdded)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub1.key, 'InstanceAddress -> dummy1Address)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub2.key, 'InstanceAddress -> dummy1Address)
    expectExactlyNEvents(1, MessageRouterActor.RouteAdded, 'Route -> componentKeyForRouteeStub1.key, 'InstanceAddress -> dummy3Address)
  }

  it should "forward 1st subscribe to the local provider" in new WithThreeNodesAndThreeComponents {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders)
    expectOneOrMoreEvents(MessageRouterActor.MessageForwarded)
    expectOneOrMoreEvents(MessageRouterActor.FirstSubjectSubscriber)
  }

  it should "not respond with cached data to the subscriber if there is nothing in the cache yet" in new WithThreeNodesAndThreeComponents {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    waitAndCheck {
      expectNoEvents(MessageRouterActor.RespondedWithCached)
    }
  }

  trait WithOneSubscriber extends WithThreeNodesAndThreeComponents {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders)
    expectOneOrMoreEvents(MessageRouterActor.MessageForwarded)
    expectOneOrMoreEvents(MessageRouterActor.FirstSubjectSubscriber)
    clearEvents()
  }

  it should "not subscribe with provider on 2nd subscription (coming from the same subscriber) if already subscribed" in new WithOneSubscriber {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    waitAndCheck {
      expectNoEvents(MessageRouterActor.ForwardedToLocalProviders)
      expectNoEvents(MessageRouterActor.MessageForwarded)
    }
  }

  it should "not subscribe with provider on 2nd subscription (coming from the same subscriber but using remote subject) if already subscribed" in new WithOneSubscriber {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    waitAndCheck {
      expectNoEvents(MessageRouterActor.ForwardedToLocalProviders)
      expectNoEvents(MessageRouterActor.MessageForwarded)
    }
  }

  it should "not subscribe with provider on 2nd subscription (coming from the new subscriber) if already subscribed" in new WithOneSubscriber {
    subscribeFrom2(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    waitAndCheck {
      expectNoEvents(MessageRouterActor.ForwardedToLocalProviders)
      expectNoEvents(MessageRouterActor.MessageForwarded)
    }
  }

  it should "not subscribe with provider on 2nd subscription (coming from the remote subscriber) if already subscribed" in new WithOneSubscriber {
    subscribeFrom1(dummy2System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address)
    waitAndCheck {
      expectNoEvents(MessageRouterActor.ForwardedToLocalProviders, 'InstanceAddress -> dummy1Address)
      expectNoEvents(MessageRouterActor.MessageForwarded, 'InstanceAddress -> dummy1Address)
    }
  }

  trait WithTwoSubscribersToInfo extends WithOneSubscriber {
    subscribeFrom2(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_INFO))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription)
    clearEvents()    
  }

  "... with two subscribers to info, message router" should "forward to the local subscriber if subscribed to the new topic" in new WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_LIST))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.MessageForwarded, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.FirstSubjectSubscriber, 'InstanceAddress -> dummy1Address)
  }

  it should "forward to the remote subscriber if subscribed to the info topic on remote host" in new WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy3Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectOneOrMoreEvents(MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToNode, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.FirstSubjectSubscriber, 'InstanceAddress -> dummy1Address)
  }

  it should "drop the message if it is sent to unknown component (correct component key but wrong address)" in new WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy2Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectOneOrMoreEvents(MessageRouterActor.MessageDropped, 'InstanceAddress -> dummy2Address)
  }

  it should "drop the message if it is sent to unknown component (incorrect component key)" in new WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy1Address, LocalSubj(ComponentKey("provider"), T_INFO)))
    expectOneOrMoreEvents(MessageRouterActor.MessageDropped, 'InstanceAddress -> dummy1Address)
  }

  trait WithThreeSubscribersToInfoAndOneToList extends WithTwoSubscribersToInfo {
    subscribeFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, T_LIST))
    subscribeFrom1(dummy2System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectExactlyNEvents(2, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address)
    clearEvents()
  }

  "... with two local one remote sub to info and one sub to list, message router" should "keep subscription for unknown component" in new WithThreeSubscribersToInfoAndOneToList {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy2Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectExactlyNEvents(1, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy2Address)
  }

  it should "auto-subscribe to the registered component if there is a pending subscription" in new WithThreeSubscribersToInfoAndOneToList {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy2Address, LocalSubj(componentKeyForRouteeStub1, T_INFO)))
    expectExactlyNEvents(1, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy2Address)
    clearEvents()
    startRouteeComponentStub1(dummy2System)
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders, 'InstanceAddress -> dummy2Address)
    expectOneOrMoreEvents(MessageRouterActor.NewSubscription, 'InstanceAddress -> dummy2Address)
  }

  it should "drop client subscription if client goes (subscriber to 1 subject, info)" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber2(dummy1System)
    expectExactlyNEvents(1, MessageRouterActor.SubjectSubscriptionRemoved)
    expectOneOrMoreEvents(MessageRouterActor.SubjectSubscriptionRemoved, 'InstanceAddress -> dummy1Address)
    waitAndCheck {
      expectNoEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved)
    }
  }

  it should "drop client subscription if client goes (subscriber to 2 subjects, info and list, only subscriber to list)" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber1(dummy1System)
    expectOneOrMoreEvents(MessageRouterActor.SubjectSubscriptionRemoved)
    expectOneOrMoreEvents(MessageRouterActor.SubjectSubscriptionRemoved, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved, 'Subject -> "provider/routeeStub1#list", 'InstanceId -> routeeIdFor(1))
  }

  it should "drop client subscription if client goes (remote subscriber)" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber1(dummy2System)
    expectExactlyNEvents(1, MessageRouterActor.SubjectSubscriptionRemoved, 'InstanceAddress -> dummy1Address)
    expectOneOrMoreEvents(MessageRouterActor.SubjectSubscriptionRemoved, 'InstanceAddress -> dummy1Address)
    waitAndCheck {
      expectNoEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved)
    }
  }

  it should "not drop provider subscription if there are client subscriptions for the subject" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber1(dummy2System)
    waitAndCheck {
      expectNoEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved)
    }
  }

  it should "not drop provider subscription if there are client subscriptions for the subject - one remote remaining" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber2(dummy1System)
    killMessageSubscriber1(dummy1System)
    waitAndCheck {
      expectNoEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved, 'Subject -> "provider/routeeStub1#info", 'InstanceId -> routeeIdFor(1))
    }
  }

  it should "drop provider subscription if there are no client subscriptions for the subject" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber2(dummy1System)
    killMessageSubscriber1(dummy1System)
    killMessageSubscriber1(dummy2System)
    expectOneOrMoreEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved, 'Subject -> "provider/routeeStub1#info", 'InstanceId -> routeeIdFor(1))
  }

  it should "drop provider subscription if there are no client subscriptions for the subject - testing with other topic - list" in new WithThreeSubscribersToInfoAndOneToList {
    killMessageSubscriber1(dummy1System)
    expectOneOrMoreEvents(RouteeComponentStubOps.SubjectSubscriptionRemoved, 'Subject -> "provider/routeeStub1#list", 'InstanceId -> routeeIdFor(1))
  }

  it should "send stale to client if provider goes" in new WithThreeSubscribersToInfoAndOneToList {
    killRouteeComponentStub1(dummy1System)
    expectOneOrMoreEvents(SubscribingComponentStub.StaleReceived, 'InstanceId -> subscriberStubInstanceIdFor(1))
    expectOneOrMoreEvents(MessageRouterActor.PendingComponentRemoval, 'InstanceAddress -> dummy1Address)
    duringPeriodInMillis(2000) {
      expectNoEvents(MessageRouterActor.RouteRemoved)
    }
  }

  it should "send stale to client if provider goes - followed by removing route" in new WithThreeSubscribersToInfoAndOneToList {
    killRouteeComponentStub1(dummy1System)
    expectOneOrMoreEvents(SubscribingComponentStub.StaleReceived, 'InstanceId -> subscriberStubInstanceIdFor(1))
    expectOneOrMoreEvents(MessageRouterActor.PendingComponentRemoval, 'InstanceAddress -> dummy1Address)
    expectSomeEventsWithTimeout(10000, MessageRouterActor.RouteRemoved)
  }

  it should "resubscribe with restarted component" in new WithThreeSubscribersToInfoAndOneToList {
    killRouteeComponentStub1(dummy1System)
    startRouteeComponentStub1(dummy1System)
    expectOneOrMoreEvents(SubscribingComponentStub.StaleReceived, 'InstanceId -> subscriberStubInstanceIdFor(1))
    expectOneOrMoreEvents(MessageRouterActor.PendingComponentRemoval, 'InstanceAddress -> dummy1Address)
    duringPeriodInMillis(10000) {
      expectNoEvents(MessageRouterActor.RouteRemoved)
    }
    expectOneOrMoreEvents(MessageRouterActor.ForwardedToLocalProviders, 'InstanceAddress -> dummy1Address)
    expectExactlyNEvents(2, MessageRouterActor.NewSubscription)
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#list@akka.tcp://hub@localhost:12521")
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
  }

  it should s"resubscribe with restarted component on another node" in new WithThreeSubscribersToInfoAndOneToList {
    restartDummyNode1()
    expectSomeEventsWithTimeout(30000, 1, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
    expectExactlyNEvents(1, MessageRouterActor.NewSubjectSubscription)
  }

  it should s"resubscribe with restarted component on another node - eventually subscribe to providers" taggedAs OnlyThisTest in new WithThreeSubscribersToInfoAndOneToList {
    restartDummyNode1()
    expectSomeEventsWithTimeout(30000, 1, MessageRouterActor.NewSubjectSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
    duringPeriodInMillis(2000) {
      expectNoEvents(MessageRouterActor.NewSubscription)
    }
    startRouteeComponentStub1(dummy1System)
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription)
    expectExactlyNEvents(1, MessageRouterActor.NewSubscription, 'InstanceAddress -> dummy1Address, 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521")
  }

  it should "drop any unsupported payload (not wraped in Option)" in new WithThreeSubscribersToInfoAndOneToList {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, TopicKey("withunsupportedresponse"))))
  }

  it should "forward response to the client if there was any" in new WithThreeSubscribersToInfoAndOneToList {
    subscribeFrom1(dummy1System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, TopicKey("withresponse"))))
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived)
    expectOneOrMoreEvents(SubscribingComponentStub.UpdateReceived, 'Contents -> "response", 'Subject -> "provider/routeeStub1#withresponse@akka.tcp://hub@localhost:12521")
  }

  it should "forward updates to the client (topic info)" in new WithThreeSubscribersToInfoAndOneToList {
    updateTopicFromRoutee1(dummy1System, T_INFO, "test")
    expectExactlyNEvents(3, SubscribingComponentStub.UpdateReceived)
    expectExactlyNEvents(2, SubscribingComponentStub.UpdateReceived, 'Contents -> "test", 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521", 'InstanceId -> "subscriberStub1")
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived, 'Contents -> "test", 'Subject -> "provider/routeeStub1#info@akka.tcp://hub@localhost:12521", 'InstanceId -> "subscriberStub2")
  }
  it should "forward updates to the client (topic list)" in new WithThreeSubscribersToInfoAndOneToList {
    updateTopicFromRoutee1(dummy1System, T_LIST, "test")
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived)
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived, 'Contents -> "test", 'Subject -> "provider/routeeStub1#list@akka.tcp://hub@localhost:12521", 'InstanceId -> "subscriberStub1")
  }
  it should "use cached value for all new subscriptions (update on list followed by new subscriber to list)" in new WithThreeSubscribersToInfoAndOneToList {
    updateTopicFromRoutee1(dummy1System, T_LIST, "test")
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived, 'Contents -> "test", 'Subject -> "provider/routeeStub1#list@akka.tcp://hub@localhost:12521", 'InstanceId -> "subscriberStub1")
    subscribeFrom2(dummy1System, RemoteAddrSubj(dummy1Address, LocalSubj(componentKeyForRouteeStub1, T_LIST)))
    expectExactlyNEvents(1, SubscribingComponentStub.UpdateReceived, 'Contents -> "test", 'Subject -> "provider/routeeStub1#list@akka.tcp://hub@localhost:12521", 'InstanceId -> "subscriberStub2")
    expectExactlyNEvents(1, MessageRouterActor.RespondedWithCached)
  }
  it should "not forward updates to the client if there are no subscribers for the topic (topic abc)" in new WithThreeSubscribersToInfoAndOneToList {
    updateTopicFromRoutee1(dummy1System, TopicKey("abc"), "test")
    waitAndCheck {
      expectNoEvents(MessageRouterActor.RespondedWithCached)
      expectNoEvents(SubscribingComponentStub.UpdateReceived)
    }
  }

  it should "forward commands to the provider" in new WithThreeSubscribersToInfoAndOneToList {
    commandFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, TopicKey("ok")), None)
    expectOneOrMoreEvents(RouteeComponentStubOps.NewCommand, 'Topic -> "ok")
  }

  it should "forward command, handle success and not forward anything to the client if there was no message" in new WithThreeSubscribersToInfoAndOneToList {
    commandFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, TopicKey("ok")), None)
    expectOneOrMoreEvents(RouteeComponentStubOps.CommandSuccessful)
  }

  it should "forward command, handle success and forward message to the client if any" in new WithThreeSubscribersToInfoAndOneToList {
    commandFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, TopicKey("okwithmessage")), None)
    expectOneOrMoreEvents(RouteeComponentStubOps.CommandSuccessful)
    expectOneOrMoreEvents(SubscribingComponentStub.CommandOkReceived, 'Contents -> "message")
  }

  it should "forward command, handle failure and not forward anything to the client if there was no message" in new WithThreeSubscribersToInfoAndOneToList {
    commandFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, TopicKey("fail")), None)
    expectOneOrMoreEvents(RouteeComponentStubOps.CommandFailed)
  }

  it should "forward command, handle failure and forward message to the client if any" in new WithThreeSubscribersToInfoAndOneToList {
    commandFrom1(dummy1System, LocalSubj(componentKeyForRouteeStub1, TopicKey("failwithmessage")), None)
    expectOneOrMoreEvents(RouteeComponentStubOps.CommandFailed)
    expectOneOrMoreEvents(SubscribingComponentStub.CommandErrReceived, 'Contents -> "message")
  }


}
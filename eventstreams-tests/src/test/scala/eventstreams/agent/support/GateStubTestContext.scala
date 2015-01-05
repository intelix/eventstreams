package eventstreams.agent.support

import eventstreams.support.GateStubActor

trait GateStubTestContext {

   def withGateStub(system: ActorSystemWrapper, name: String) =
     system.start(GateStubActor.props, name)


 }

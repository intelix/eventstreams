package eventstreams.support

import actors.{AuthorizationUpdate, WebsocketActor}
import akka.actor.{ActorRef, Props}
import com.diogoduailibe.lzstring4j.LZString
import core.sysevents.SyseventOps.stringToSyseventOps
import core.sysevents.WithSyseventPublisher
import core.sysevents.ref.ComponentWithBaseSysevents
import eventstreams.core.actors.{ActorWithComposableBehavior, BaseActorSysevents}

trait WebsocketActorTestContext extends WebsocketClientStub {

  def startWebsocketActor(system: ActorSystemWrapper, id: String) = {
    val out = startWebsocketClientStub(system, "client_" + id)
    system.start(WebsocketActor.props(out), id)
  }

  def sendToWebsocket(system: ActorSystemWrapper, id: String, msg: String, compressed: Boolean) =
    system.rootUserActorSelection(id) ! (if (compressed) "z" + LZString.compressToUTF16(msg) else "f" + msg)
  def sendToWebsocketRaw(system: ActorSystemWrapper, id: String, msg: String) =
    system.rootUserActorSelection(id) ! msg

}


trait WebsocketClientStubSysevents extends ComponentWithBaseSysevents with BaseActorSysevents {
  val WebsocketMessageReceived = "WebsocketMessageReceived".info
  val PayloadDecompressed = "PayloadDecompressed".info
  val InvalidPayload = "InvalidPayload".error

  val WebsocketAddressReceived = "WebsocketAddressReceived".info
  val WebsocketUpdateReceived = "WebsocketUpdateReceived".info
  val WebsocketAuthUpdateReceived = "WebsocketAuthUpdateReceived".info
  val WebsocketStaleReceived = "WebsocketStaleReceived".info


  override def componentId: String = "Test.WebsocketClientStub"
}

trait WebsocketClientStub extends WebsocketClientStubSysevents {
  def props(instanceId: String) = Props(new WebsocketClientStubActor(instanceId))

  def startWebsocketClientStub(sys: ActorSystemWrapper, id: String): ActorRef = {
    sys.start(props(id), id)
  }
}

object WebsocketClientStub extends WebsocketClientStub

class WebsocketClientStubActor(instanceId: String)
  extends ActorWithComposableBehavior with WebsocketClientStubSysevents with WithSyseventPublisher {
  override def commonBehavior: Receive = handler orElse super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('InstanceId -> instanceId)

  def decode(s: String) = s match {
    case x if x.startsWith("L") =>
      WebsocketAddressReceived >> ('Value -> x.tail)
    case x : String =>
      val arr = x.tail.split(WebsocketActor.opSplitChar)
      val alias = arr(0)
      x.head match {
        case 'A' => WebsocketAuthUpdateReceived >> ()
        case 'U' => WebsocketUpdateReceived >> ('Alias -> alias, 'Payload -> arr(1))
        case 'D' => WebsocketStaleReceived >> ('Alias -> alias)
        case z => InvalidPayload >> ('InvalidType -> z)
      }


  }

  def split(s: String) = s.split(WebsocketActor.msgSplitChar).foreach(decode)

  def uncompress(s: Any) = s match {
    case x: String if x.startsWith("z") =>
      PayloadDecompressed >>()
      split(LZString.decompressFromUTF16(x.tail))
    case x: String if x.startsWith("f") => split(x.tail)
    case x => InvalidPayload >>()
  }

  def handler: Receive = {
    case x =>
      WebsocketMessageReceived >> ()
      uncompress(x)
  }
}


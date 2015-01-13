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

package actors

import actors.WebsocketActor.{msgSplitChar, opSplitChar}
import akka.actor.{Actor, ActorRef, Props}
import com.diogoduailibe.lzstring4j.LZString
import core.events.EventOps.symbolToEventOps
import core.events.WithEventPublisher
import core.events.ref.ComponentWithBaseEvents
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithTicks, BaseActorEvents}
import eventstreams.core.messages._
import play.api.libs.json.{JsValue, Json}

import scala.collection._
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Try


trait WebsocketActorEvents
  extends ComponentWithBaseEvents
  with BaseActorEvents
  with WithWebEvents {


  val AcceptedConnection = 'AcceptedConnection.info
  val ClosedConnection = 'ClosedConnection.info
  val SendingClusterAddress = 'SendingClusterAddress.trace
  val WebsocketOut = 'WebsocketOut.trace
  val WebsocketIn = 'WebsocketIn.trace
  val Request = 'Request.trace
  val MessageToDownstream = 'MessageToDownstream.trace
  val NewCmdAlias = 'NewCmdAlias.trace
  val NewLocationAlias = 'NewLocationAlias.trace
  val UserUUID = 'UserUUID.info
  val MessageScheduled = 'MessageScheduled.trace
  val SentToClient = 'SentToClient.trace

  override def componentId: String = "WebsocketActor"
}

object WebsocketActor extends WebsocketActorEvents {

  def props(out: ActorRef) = Props(new WebsocketActor(out))

  val opSplitChar: Char = 1.toChar
  val msgSplitChar: Char = 2.toChar

}


class WebsocketActor(out: ActorRef)
  extends ActorWithComposableBehavior
  with ActorWithTicks
  with WebsocketActorEvents
  with WithEventPublisher {

  val alias2path: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val path2alias: mutable.Map[String, String] = new mutable.HashMap[String, String]()

  val alias2location: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val location2alias: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  val proxy = RouterActor.path
  var aggregator: mutable.Map[String, String] = new mutable.HashMap[String, String]()
  var clientSeed: Option[String] = None
  var cmdReplySubj: Option[LocalSubj] = None

  override def tickInterval: FiniteDuration = 200.millis

  override def preStart(): Unit = {
    super.preStart()

    AcceptedConnection >>('ProxyActor -> out, 'ThisActor -> self)

    LocalClusterAwareActor.path ! InfoRequest()
  }


  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop()
    ClosedConnection >>('ProxyActor -> out, 'ThisActor -> self)
  }

  override def commonBehavior: Actor.Receive = messageHandler orElse super.commonBehavior

  override def processTick(): Unit = {
    val str = aggregator.values.foldRight("") { (value, aggr) =>
      if (aggr != "") {
        aggr + msgSplitChar + value
      } else {
        value
      }
    }
    if (str.length > 0) {
      var msg = ""
      if (str.length > 100) {
        val comp = LZString.compressToUTF16(str)
        if (comp.length < str.length) {
          msg = "z" + comp
        }
      }
      if (msg == "") msg = "f" + str
      sendToSocket(msg)
      SentToClient >>('Count -> aggregator.size, 'UncompLen -> str.length, 'CompLen -> msg.length)
      aggregator.clear()
    }
  }

  private def sendToSocket(msg: String) = {
    WebsocketOut >> ('Len -> msg.length)
    out ! msg
  }


  private def messageHandler: Actor.Receive = {
    case InfoResponse(address) =>
      SendingClusterAddress >> ('Address -> address)
      sendToSocket("fL" + address.toString)
    case Update(sourceRef, subj, data, _) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))}
    case CommandErr(sourceRef, subj, data) =>
      val path = subj2path(subj)
      val alias = path2alias get path
      alias foreach { path =>
        scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))
      }
    case CommandOk(sourceRef, subj, data) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("U", path)(Json.stringify(data)))}
    case Stale(sourceRef, subj) =>
      path2alias get subj2path(subj) foreach { path => scheduleOut(path, buildClientMessage("D", path)())}

    case payload: String if payload.length > 0 =>
  
      val flag = payload.head
      val d = flag match {
        case 'z' =>
          val uncompressed = Try {
            LZString.decompressFromUTF16(payload.tail)
          } recover {
            case _ => ""
          } getOrElse ""
          if (uncompressed != null) uncompressed else ""
        case _ => payload.tail
      }

      WebsocketIn >>('Message -> d, 'UncompLen -> d.length, 'CompLen -> payload.length)

      d.split(msgSplitChar).foreach {
        case msgContents if msgContents.length > 0 =>
          val mtype = msgContents.head
          val data = msgContents.tail

          mtype match {
            case 'X' => addUUID(data)
            case 'A' => addOrReplaceAlias(data)
            case 'B' => addOrReplaceLocationAlias(data)
            case _ => extractByAlias(data) foreach { str =>
              Request >> ('Request -> str)
              extractSubjectAndPayload(str,
                processRequestByType(mtype, _, _) foreach { msg =>
                  MessageToDownstream >> ('Message -> str)
                  proxy ! msg
                }
              )
            }
          }
        case _ => ()
      }
    case _ => ()
      

  }

  private def scheduleOut(path: String, content: String) = {
    aggregator += path -> content
    MessageScheduled >>('Path -> path, 'Message -> content)
  }

  private def buildClientMessage(mt: String, alias: String)(payload: String = "") = {
    mt + alias + opSplitChar + payload
  }

  private def subj2path(subj: Any) = subj match {
    case RemoteSubj(addr, LocalSubj(ComponentKey(compKey), TopicKey(topicKey))) =>
      mapComponents(compKey).map(segments2path(location2alias.getOrElse(addr, addr), _, topicKey)).getOrElse("invalid")
    case LocalSubj(ComponentKey(compKey), TopicKey(topicKey)) =>
      mapComponents(compKey).map(segments2path("_", _, topicKey)).getOrElse("invalid")
    case _ => "invalid"
  }

  private def segments2path(addr: String, component: String, topic: String) = addr + opSplitChar + component + opSplitChar + topic

  private def addOrReplaceAlias(value: String) = {

    val idx: Int = value.indexOf(opSplitChar)

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    NewCmdAlias >>('Name -> al, 'Path -> path)

    alias2path += al -> path
    path2alias += path -> al
  }

  private def addUUID(value: String) = {

    UserUUID >> ('UUID -> value)

    clientSeed = Some(value)
    cmdReplySubj = Some(LocalSubj(ComponentKey(value), TopicKey("cmd")))

    proxy ! RegisterComponent(ComponentKey(value), self)

  }

  private def addOrReplaceLocationAlias(value: String) = {

    val idx: Int = value.indexOf(opSplitChar)

    val al = value.substring(0, idx)
    val path = value.substring(idx + 1)

    NewLocationAlias >>('Name -> al, 'Location -> path)

    alias2location += al -> path
    location2alias += path -> al
  }

  private def processRequestByType(msgType: Char, subj: Subj, payload: Option[JsValue]) = msgType match {
    case 'S' => Some(Subscribe(self, subj))
    case 'U' => Some(Unsubscribe(self, subj))
    case 'C' => Some(Command(self, subj, cmdReplySubj, payload))
    case _ =>
      Error >> ('Message -> s"Invalid message type: $msgType")
      None
  }

  private def extractByAlias(value: String): Option[String] = {
    val idx: Int = value.indexOf(opSplitChar)
    if (idx > -1) {
      val al = value.substring(0, idx)
      val path = value.substring(idx)

      alias2path.get(al).map(_ + path)
    } else None
  }

  private def mapComponents(comp: String): Option[String] = {
    comp match {
      case "_" => None
      case x if clientSeed.isDefined && clientSeed.get == x => Some("_")
      case other => Some(other)
    }
  }

  private def extractSubjectAndPayload(str: String, f: (Subj, Option[JsValue]) => Unit) = {
    def extractPayload(list: List[String]) = list match {
      case Nil => None
      case x :: xs => Json.parse(x).asOpt[JsValue]
    }
    def extract(list: List[String]) = list match {
      case "_" :: comp :: topic :: tail =>
        mapComponents(comp) foreach { mappedComp =>
          f(LocalSubj(ComponentKey(mappedComp), TopicKey(topic)), extractPayload(tail))
        }
      case addr :: comp :: topic :: tail => mapComponents(comp) foreach { mappedComp =>
        alias2location.get(addr).foreach { loc =>
          f(RemoteSubj(loc, LocalSubj(ComponentKey(mappedComp), TopicKey(topic))), extractPayload(tail))
        }
      }
      case _ =>
        Warning >> ('Message -> s"Invalid payload $str")
    }

    extract(str.split(opSplitChar).toList)
  }

}

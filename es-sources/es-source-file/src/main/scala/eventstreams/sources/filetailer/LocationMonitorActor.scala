package eventstreams.sources.filetailer

import java.nio.charset.Charset

import _root_.core.sysevents.WithSyseventPublisher
import akka.actor.Props
import akka.util.ByteString
import eventstreams.Tools.configHelper
import eventstreams._
import eventstreams.core.actors._
import play.api.libs.json.{JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz.\/

object LocationMonitorActor {
  def props(streamKey: String, props: JsValue, cursor: Option[JsValue])(implicit fileSystem: FileSystem) = Props(new LocationMonitorActor(streamKey, props, cursor))
}

class LocationMonitorActor(val streamKey: String, props: JsValue, cursor: Option[JsValue])(implicit val fileSystem: FileSystem)
  extends ActorWithComposableBehavior
  with ActorWithActivePassiveBehaviors
  with ActorWithTicks
  with FileTailerConstants
  with PullingPublisher[EventAndCursor]
  with FileHandler
  with MonitoringTarget
  with InMemoryResourceCatalogComponent
  with FileSystemComponent
  with WithSyseventPublisher {

  currentCursor = FileCursorTools.fromJson(cursor)

  override val initialPosition: InitialPosition = (props ~> CfgFStartWith).map(_.toLowerCase) match {
    case Some("first") => StartWithFirst()
    case _ => StartWithLast()
  }
  override val directory: String = (props ~> CfgFDirectory).get
  override val rolledFilePatternR: Option[Regex] = (props ~> CfgFRolledPattern).map(new Regex(_))
  override val mainLogPatternR: Regex = new Regex((props ~> CfgFMainPattern).get)
  override val charset: Charset = Charset.forName(props ~> CfgFCharset | "UTF-8")
  override val fileOrdering: FileOrdering = (props ~> CfgFFileOrdering).map(_.toLowerCase) match {
    case Some("name only") => OrderByNameOnly()
    case _ => OrderByLastModifiedAndName()
  }
  override val blockSize: Int = props +> CfgFBlockSize | 16 * 1024

  override def commonBehavior: Receive = super.commonBehavior

  override def commonFields: Seq[(Symbol, Any)] = super.commonFields ++ Seq('StreamKey -> streamKey)


  override def preStart(): Unit = {
    EventsourceInstance >>(
      'Directory -> directory,
      'MainLogPattern -> props ~> CfgFMainPattern,
      'RolledLogPattern -> props ~> CfgFRolledPattern,
      'Ordering -> props ~> CfgFFileOrdering,
      'InitialPosition -> props ~> CfgFStartWith)
    super.preStart()
  }

  override def produceNext(maxCount: Int): Option[Seq[StreamElement]] =
    for (
      chunk <- pullNextChunk();
      data <- chunk.data
    ) yield List(ScheduledEvent(EventAndCursor(convertPayload(data, chunk.meta), FileCursorTools.toJson(chunk.cursor))))

  override def inactivityThresholdMs: Int = props +> CfgFInactivityThresholdMs | 60 * 1000

  private def convertPayload(b: ByteString, meta: Option[JsValue]) = EventFrame(
    "value" -> b.utf8String,
    "meta" -> EventFrameConverter.fromJson(meta | Json.obj())
  )
}

package eventstreams.sources.filetailer

import java.nio.charset.Charset

import akka.actor.Props
import akka.util.ByteString
import core.sysevents.WithSyseventPublisher
import eventstreams.JSONTools.configHelper
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithTicks, PipelineWithStatesActor, StoppablePublisherActor}
import eventstreams.{EventFrame, EventFrameConverter, ProducedMessage, JSONTools}
import play.api.libs.json.{JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._

object LocationMonitorActor {
  def props(eventsourceId: String, props: JsValue, cursor: Option[JsValue])(implicit fileSystem: FileSystem) = Props(new LocationMonitorActor(eventsourceId, props, cursor))
}

class LocationMonitorActor(val eventsourceId: String, props: JsValue, cursor: Option[JsValue])(implicit val fileSystem: FileSystem)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithTicks
  with FileTailerConstants
  with StoppablePublisherActor[ProducedMessage]
  with FileHandler
  with MonitoringTarget
  with InMemoryResourceCatalogComponent
  with FileSystemComponent
  with WithSyseventPublisher {

  currentCursor = FileCursorTools.fromJson(cursor)

  override def commonBehavior: Receive = super.commonBehavior

  override def preStart(): Unit = {
    EventsourceInstance >>(
      'Directory -> directory,
      'MainLogPattern -> props ~> CfgFMainPattern,
      'RolledLogPattern -> props ~> CfgFRolledPattern,
      'Ordering -> props ~> CfgFFileOrdering,
      'InitialPosition -> props ~> CfgFStartWith)
    super.preStart()
  }

  private def convertPayload(b: ByteString, meta: Option[JsValue]) = EventFrame(
    "value" -> b.utf8String,
    "meta" -> EventFrameConverter.fromJson( meta | Json.obj() )
  )

  override def produceMore(count: Long): Option[Seq[ProducedMessage]] = for (
    chunk <- pullNextChunk();
    data <- chunk.data
  ) yield List(ProducedMessage(convertPayload(data, chunk.meta), FileCursorTools.toJson(chunk.cursor)))


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

  override def inactivityThresholdMs: Int = props +> CfgFInactivityThresholdMs | 60 * 1000
}

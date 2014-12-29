package eventstreams.ds.plugins.filetailer

import java.nio.charset.Charset

import akka.actor.Props
import akka.util.ByteString
import eventstreams.core.Tools.configHelper
import eventstreams.core.actors.{ActorWithComposableBehavior, ActorWithTicks, PipelineWithStatesActor, StoppablePublisherActor}
import eventstreams.core.agent.core.ProducedMessage
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.matching.Regex
import scalaz.Scalaz._

object LocationMonitorActor {
  def props(props: JsValue) = Props(new LocationMonitorActor(props))
}

class LocationMonitorActor(props: JsValue)
  extends ActorWithComposableBehavior
  with PipelineWithStatesActor
  with ActorWithTicks
  with FileTailerConstants
  with StoppablePublisherActor[ProducedMessage]
  with FileHandler
  with MonitoringTarget
  with InMemoryResourceCatalogComponent
  with FileSystemComponent {


  override def commonBehavior: Receive = super.commonBehavior

  private def convertPayload(b: ByteString, meta: Option[JsValue]) = Json.obj(
    "value" -> JsString(b.utf8String),
    "meta" -> (meta | Json.obj())
  )

  override def produceMore(count: Long): Option[Seq[ProducedMessage]] = for (
    chunk <- pullNextChunk();
    data <- chunk.data
  ) yield {
    currentCursor = Some(chunk.cursor)
    List(ProducedMessage(convertPayload(data, chunk.meta), FileCursorTools.toJson(chunk.cursor)))
  }


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

  override lazy val fileSystem: FileSystem = new DiskFileSystem()
}

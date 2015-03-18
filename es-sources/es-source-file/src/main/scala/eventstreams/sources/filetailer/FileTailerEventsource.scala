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

package eventstreams.sources.filetailer

import java.nio.charset.Charset

import akka.actor.Props
import core.sysevents.WithSyseventPublisher
import eventstreams.Tools.{optionsHelper, configHelper}
import eventstreams.core.actors.{StandardPublisherSysevents, StateChangeSysevents}
import eventstreams.{BuilderFromConfig, Fail, OK, Tools}
import play.api.libs.json.{JsValue, Json}

import scala.util.Try
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._


sealed trait InitialPosition

case class StartWithLast() extends InitialPosition

case class StartWithFirst() extends InitialPosition

sealed trait FileOrdering

case class OrderByLastModifiedAndName() extends FileOrdering

case class OrderByNameOnly() extends FileOrdering


trait FileTailerConstants
  extends FileTailerSysevents
  with StateChangeSysevents
  with StandardPublisherSysevents {
  val CfgID = "file"
  val CfgFDirectory = "directory"
  val CfgFMainPattern = "mainPattern"
  val CfgFRolledPattern = "rolledPattern"
  val CfgFStartWith = "startWith"
  val CfgFFileOrdering = "fileOrdering"
  val CfgFCharset = "charset"
  val CfgFBlockSize = "blockSize"
  val CfgFInactivityThresholdMs = "inactivityThresholdMs"

}

object FileTailerConstants extends FileTailerConstants

class FileTailerEventsource extends BuilderFromConfig[Props] with FileTailerSysevents with FileTailerConstants with WithSyseventPublisher {
  override def configId: String = CfgID

  def build(config: JsValue, maybeState: Option[JsValue], id: Option[String] = None): \/[Fail, Props] = {
    implicit val fileSystem = new DiskFileSystem()
    for (
      streamSeed <- id orFail s"streamSeed must be provided";
      streamKey <- config ~> 'streamKey orFail s"streamKey must be provided";
      props <- config #> 'source orFail s"Invalid configuration";
      _ <- props ~> CfgFDirectory orFail s"Invalid $configId eventsource. Missing '$CfgFDirectory' value. Contents: ${Json.stringify(props)}";
      mainPattern <- props ~> CfgFMainPattern orFail s"Invalid $configId eventsource. Missing '$CfgFMainPattern' value. Contents: ${Json.stringify(props)}";
      _ <- Try {
        new Regex(mainPattern)
      }.toOption orFail s"Invalid $configId eventsource. Invalid '$CfgFMainPattern' value. Contents: ${Json.stringify(props)}";
      _ <- Try {
        (props ~> CfgFRolledPattern).map(new Regex(_))
      }.toOption orFail s"Invalid $configId eventsource. Invalid '$CfgFRolledPattern' value. Contents: ${Json.stringify(props)}";
      _ <- Try {
        Charset.forName(props ~> CfgFCharset | "UTF-8")
      }.toOption orFail s"Invalid $configId eventsource. Invalid '$CfgFCharset' value. Contents: ${Json.stringify(props)}";
      _ <- if ((props +> CfgFBlockSize | 16*1024) < 32)
        Fail(s"Invalid $configId eventsource. Invalid '$CfgFBlockSize' value. Must be more than 32. Contents: ${Json.stringify(props)}")
      else OK()
    ) yield {
      Built >>('Config -> props, 'State -> maybeState)
      LocationMonitorActor.props(streamKey, props, maybeState)
    }
  }
}

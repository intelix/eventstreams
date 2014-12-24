/*
 * Copyright 2014 Intelix Pty Ltd
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

package eventstreams.plugins.essentials

import eventstreams.core.Tools.{configHelper, _}
import eventstreams.core.Types.SimpleInstructionType
import eventstreams.core._
import eventstreams.core.instructions.SimpleInstructionBuilder
import play.api.libs.json._
import play.api.libs.json.extensions._

import scala.annotation.tailrec
import scala.util.matching.Regex
import scalaz.Scalaz._
import scalaz._

class SplitInstruction extends SimpleInstructionBuilder with NowProvider {
  val configId = "split"

  override def simpleInstruction(props: JsValue, id: Option[String] = None): \/[Fail, SimpleInstructionType] =
    for (
      source <- props ~> 'source \/> Fail(s"Invalid split instruction. Missing 'source' value. Contents: ${Json.stringify(props)}");
      pattern <- (props ~> 'pattern).map(new Regex(_)) \/> Fail(s"Invalid split instruction. Missing 'pattern' value. Contents: ${Json.stringify(props)}")
    ) yield {

      var sequence: Long = 0
      var remainder: Option[String] = None

      @tailrec
      def ext(list: List[String], remainder: Option[String]): (List[String], Option[String]) =
        remainder.flatMap(pattern.findFirstMatchIn(_)) match {
          case None => (list, remainder)
          case Some(m) => ext(list :+ m.group(1), Some(m.group(2)))
        }

      fr: JsonFrame => {

        val sourceId = fr.event ~> 'eventId | "!" + Utils.generateShortUUID
        val eventSeq = fr.event ++> 'eventSeq | now

        val baseEventSeq = eventSeq << 16

        val keepOriginalEvent = props ?> 'keepOriginalEvent | false
        val additionalTags = (props ~> 'additionalTags | "").split(",").map(_.trim)

        val index = props ~> 'index | "${index}"
        val table = props ~> 'table | "${table}"
        val ttl = props ~> 'ttl | "${_ttl}"


        val sourceField = macroReplacement(fr, JsString(source))


        sequence = sequence + 1

        val str = Some(remainder.getOrElse("") + locateFieldValue(fr, sourceField).asOpt[String].getOrElse(""))

        logger.debug(s"Before split $sequence, remainder: $remainder and complete string: $str")

        val (resultList, newRemainder) = ext(List(), str)

        remainder = newRemainder

        logger.debug(s"After split $sequence, events: ${resultList.size}, remainder: $newRemainder")

        var counter = 0


        val result = resultList.map(_.trim).filter(!_.isEmpty).map { value =>
          var event = setValue("s", JsString(value), toPath(sourceField), fr.event).set(
            __ \ 'eventId -> JsString(sourceId + ":" + counter),
            __ \ 'eventSeq -> JsNumber(baseEventSeq + counter),
            __ \ 'splitSequence -> JsNumber(sequence)
          )

          event = setValue("s", JsString(macroReplacement(fr, index)), __ \ 'index, event)
          event = setValue("s", JsString(macroReplacement(fr, table)), __ \ 'table, event)
          event = setValue("s", JsString(macroReplacement(fr, ttl)), __ \ '_ttl, event)

          additionalTags.foreach { tag =>
            event = setValue("as", JsString(tag), __ \ 'tags, event)
          }


          counter = counter + 1

          JsonFrame(event, fr.ctx)

        }

        if (keepOriginalEvent) result :+ fr else result

      }
    }


}
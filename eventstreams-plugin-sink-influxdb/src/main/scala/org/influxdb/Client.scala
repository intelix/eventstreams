/*
The MIT License (MIT)

Copyright (c) 2013 InfluxDB

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.influxdb

import java.net.URLEncoder
import java.util.concurrent.{Future, TimeUnit}

import com.ning.http.client.{AsyncHttpClient, Response}
import play.api.libs.json.{JsValue, Json}


class Client(host: String = "localhost:8086", var username: String = "root", var password: String = "root", var database: String = "", schema: String = "http") {
  private val httpClient = new AsyncHttpClient()

  var (timeout, unit) = (3, TimeUnit.SECONDS)

  def close() {
    httpClient.close()
  }

  def ping: error.Error = {
    try {
      val url = getUrl("/ping")
      responseToError(getResponse(httpClient.prepareGet(url).execute()))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }

  def query(query: String, timePrecision: Option[String] = None, chunked: Boolean = false): (response.Response, error.Error) = {
    try {
      val q = URLEncoder.encode(query, "UTF-8")
      val url = getUrl(s"/db/$database/series") + s"&q=$q&chunked=$chunked" +
        (if (timePrecision.isDefined) s"&time_precision=${timePrecision.get}" else "")

      val r = getResponse(httpClient.prepareGet(url).execute())
      responseToError(r) match {
        case None => (response.Response(r.getResponseBody), None)
        case Some(err) => (null, Some(err))
      }
    } catch {
      case ex: Exception => (null, Some(ex.getMessage))
    }
  }


  def writeSeries(series: JsValue): error.Error = writeSeriesCommon(series, None)

  def writeSeriesWithTimePrecision(series: JsValue, timePrecision: String): error.Error = {
    writeSeriesCommon(series, Some(Map[String, String]("time_precision" -> timePrecision)))
  }

  private def writeSeriesCommon(series: JsValue, options: Option[Map[String, String]]): error.Error = {
    try {
      val url = getUrl(s"/db/$database/series") + (if (options.isDefined) options.get.map { o => val (k, v) = o; s"$k=$v" }.mkString("&", "&", "") else "")
      val data = Json.stringify(series)

      val fr = httpClient.preparePost(url).addHeader("Content-Type", "application/json").setBody(data).execute()

//      println(s"!>>>> BODY: $data")

      responseToError(getResponse(fr))
    } catch {
      case ex: Exception => Some(ex.getMessage)
    }
  }
  private def responseToError(r: Response): error.Error = {
    if (r.getStatusCode >= 200 && r.getStatusCode < 300) {
      return None
    }
    return Some(s"Server returned (${r.getStatusText}): ${r.getResponseBody}")
  }
  private def getResponse(fr: Future[Response]): Response = fr.get(timeout, unit)
  private def getUrlWithUserAndPass(path: String, username: String, password: String): String = s"$schema://$host$path?u=$username&p=$password"
  private def getUrl(path: String) = getUrlWithUserAndPass(path, username, password)
}

import scala.util.parsing.json.JSON

package object response {
  case class Database(name: String, replicationFactor: Int)
  case class ClusterAdmin(username: String)
  case class ContinuousQuery(id: Int, query: String)
  case class Response(json: String) {

    def toSeries: Array[Series] = {
      val all = JSON.parseFull(json).get.asInstanceOf[List[Any]]
      val series = new Array[Series](all.length)

      var i = 0
      all.foreach { ai =>
        val m = ai.asInstanceOf[Map[String, Any]]
        val name = m.get("name").get.asInstanceOf[String]
        val columns = m.get("columns").get.asInstanceOf[List[String]].toArray
        val points = m.get("points").get.asInstanceOf[List[List[Any]]].map(li => li.toArray).toArray

        series(i) = Series(name, columns, points)
        i += 1
      }
      series
    }

    def toSeriesMap: Array[SeriesMap] = {
      val all = JSON.parseFull(json).get.asInstanceOf[List[Any]]
      val series = new Array[SeriesMap](all.length)

      var i = 0
      all.foreach { ai =>
        val m = ai.asInstanceOf[Map[String, Any]]
        val name = m.get("name").get.asInstanceOf[String]
        val columns = m.get("columns").get.asInstanceOf[List[String]]

        var ii = 0
        val mm = scala.collection.mutable.Map[String, Array[Any]]()
        val cc = new Array[String](columns.size)
        columns.foreach { cl => cc(ii) = cl; mm(cl) = Array[Any](); ii += 1 }

        m.get("points").get.asInstanceOf[List[List[Any]]].foreach { pt =>
          ii = 0
          pt.foreach { v => mm += cc(ii) -> (mm(cc(ii)) :+ v); ii += 1; }
        }
        series(i) = SeriesMap(name, mm.toMap)
        i += 1
      }
      series
    }
  }
}

package object error {
  type Error = Option[String]
}

case class Series(name: String, columns: Array[String], points: Array[Array[Any]])
case class SeriesMap(name: String, objects: Map[String, Array[Any]])
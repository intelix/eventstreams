package eventstreams.sources.filetailer

import java.nio.charset.Charset

import scala.util.matching.Regex

trait MonitoringTarget {

  def initialPosition: InitialPosition

  def streamKey: String

  def directory: String

  def rolledFilePatternR: Option[Regex]

  def mainLogPatternR: Regex

  def fileOrdering: FileOrdering

  def charset: Charset
  
  def blockSize: Int

  def inactivityThresholdMs: Int

}

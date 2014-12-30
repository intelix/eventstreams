package eventstreams.ds.plugins.filetailer

import core.events.EventOps.symbolToEventOps
import core.events.ref.ComponentWithBaseEvents

trait FileTailerEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  val DatasourceInstance = 'DatasourceInstance.info

  val Opened = 'Opened.info
  val Closed = 'Closed.info

  val SkippedTo = 'SkippedTo.trace

  val NoData = 'NoData.trace
  val NewDataBlock = 'NewDataBlock.trace

  val ResourceCatalogUpdate = 'ResourceCatalogUpdate.trace

  override def componentId: String = "Datasource.FileTailer"
}
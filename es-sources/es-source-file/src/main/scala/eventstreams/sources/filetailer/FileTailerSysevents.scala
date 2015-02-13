package eventstreams.sources.filetailer

import core.sysevents.SyseventOps.symbolToSyseventOps
import core.sysevents.ref.ComponentWithBaseSysevents

trait FileTailerSysevents extends ComponentWithBaseSysevents {

  val Built = 'Built.trace
  val EventsourceInstance = 'EventsourceInstance.info

  val Opened = 'Opened.info
  val Closed = 'Closed.info

  val SkippedTo = 'SkippedTo.trace

  val NoData = 'NoData.trace
  val NewDataBlock = 'NewDataBlock.trace

  val ResourceCatalogUpdate = 'ResourceCatalogUpdate.trace
  
  val ResourceCatalogNewSeed = 'ResourceCatalogNewSeed.trace

  override def componentId: String = "Eventsource.FileTailer"
}
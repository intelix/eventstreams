package eventstreams.ds.plugins.filetailer

import core.events.EventOps.symbolToEventOps
import core.events.ref.ComponentWithBaseEvents

trait FailTailerEvents extends ComponentWithBaseEvents {

  val Built = 'Built.trace
  
  override def componentId: String = "Datasource.FileTailer"
}
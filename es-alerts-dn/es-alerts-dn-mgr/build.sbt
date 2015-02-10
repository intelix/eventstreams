
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-alerts-dn-mgr")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
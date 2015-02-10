
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-gate-mgr")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
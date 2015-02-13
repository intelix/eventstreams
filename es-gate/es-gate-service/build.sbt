
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-gate-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
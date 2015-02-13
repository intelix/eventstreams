
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-gate-api")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
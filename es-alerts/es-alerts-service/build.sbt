
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-alerts-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
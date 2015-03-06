
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-alerts-api")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
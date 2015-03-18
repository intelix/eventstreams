
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-gauges-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
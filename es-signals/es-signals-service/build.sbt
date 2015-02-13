
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-signals-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
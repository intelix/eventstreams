
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-signals-api")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
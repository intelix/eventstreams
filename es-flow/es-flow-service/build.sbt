
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-flow-service")

parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
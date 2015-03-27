
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-healthmon-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
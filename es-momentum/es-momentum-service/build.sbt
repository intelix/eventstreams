
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-momentum-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
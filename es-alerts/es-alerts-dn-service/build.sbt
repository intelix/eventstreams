
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-alerts-dn-service")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
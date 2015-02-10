
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-signals-mgr")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
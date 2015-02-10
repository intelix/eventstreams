
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-flow-mgr")

parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
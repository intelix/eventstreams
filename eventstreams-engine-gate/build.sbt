
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-engine-gate")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
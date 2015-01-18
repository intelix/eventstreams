
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-engine-flow")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
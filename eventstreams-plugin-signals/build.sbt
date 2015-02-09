
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-plugin-signals")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
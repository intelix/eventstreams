
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-plugin-notifications-desktop-core")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsEngines
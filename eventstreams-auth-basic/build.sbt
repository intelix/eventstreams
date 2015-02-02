import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-auth-basic")

libraryDependencies ++= Dependencies.eventStreamsEngines


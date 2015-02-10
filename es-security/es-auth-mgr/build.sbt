import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-auth-mgr")

libraryDependencies ++= Dependencies.eventStreamsEngines


import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-auth-service")

libraryDependencies ++= Dependencies.eventStreamsEngines


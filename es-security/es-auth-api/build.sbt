import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-auth-api")

libraryDependencies ++= Dependencies.eventStreamsEngines


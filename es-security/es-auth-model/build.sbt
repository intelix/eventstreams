import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-auth-model")

libraryDependencies ++= Dependencies.eventStreamsEngines


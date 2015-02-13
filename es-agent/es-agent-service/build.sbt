import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-service")

libraryDependencies ++= Dependencies.eventStreamsEngines




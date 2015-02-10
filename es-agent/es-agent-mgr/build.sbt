import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-mgr")

libraryDependencies ++= Dependencies.eventStreamsEngines




import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-api")

libraryDependencies ++= Dependencies.eventStreamsCore


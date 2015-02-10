import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-model")

libraryDependencies ++= Dependencies.eventStreamsCore


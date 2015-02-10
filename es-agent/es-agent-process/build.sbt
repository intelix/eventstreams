import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-process")

libraryDependencies ++= Dependencies.eventStreamsCore


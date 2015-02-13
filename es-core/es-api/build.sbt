import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-api")

libraryDependencies ++= Dependencies.eventStreamsCore


import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-instructions-api")

libraryDependencies ++= Dependencies.eventStreamsCore


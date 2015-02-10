import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-instructions-model")

libraryDependencies ++= Dependencies.eventStreamsCore


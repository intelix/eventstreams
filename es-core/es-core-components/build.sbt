import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-core-components")

libraryDependencies ++= Dependencies.eventStreamsCore


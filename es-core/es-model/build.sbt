import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-model")

libraryDependencies ++= Dependencies.eventStreamsCommon


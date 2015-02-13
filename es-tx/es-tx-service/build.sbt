import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-tx-service")

libraryDependencies ++= Dependencies.eventStreamsCommon

parallelExecution in Test := false






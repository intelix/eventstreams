import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-tx-mgr")

libraryDependencies ++= Dependencies.eventStreamsCommon

parallelExecution in Test := false






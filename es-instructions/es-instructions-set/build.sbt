import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-instructions-set")

libraryDependencies ++= Dependencies.eventStreamsPluginInstructionsEssentials

parallelExecution in Test := false






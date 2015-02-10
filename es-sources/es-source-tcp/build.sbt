import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-tcp")

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials




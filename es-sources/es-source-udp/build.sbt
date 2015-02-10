import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-udp")

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials




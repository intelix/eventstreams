import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-statsd")

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials




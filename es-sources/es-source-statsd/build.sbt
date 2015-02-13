import eventstreams.{Dependencies, EventStreamsBuild}

EventStreamsBuild.coreSettings("es-source-statsd")

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials




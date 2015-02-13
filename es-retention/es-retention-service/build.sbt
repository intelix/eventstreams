import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-retention-service")

libraryDependencies ++= Dependencies.eventStreamsPluginElasticsearch




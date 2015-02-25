import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-elastic")

libraryDependencies ++= Dependencies.eventStreamsPluginElasticsearch


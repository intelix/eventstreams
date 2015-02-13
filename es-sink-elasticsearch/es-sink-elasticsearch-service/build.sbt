import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-sink-elasticsearch-service")

libraryDependencies ++= Dependencies.eventStreamsPluginElasticsearch




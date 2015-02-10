import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-sink-elasticsearch-mgr")

libraryDependencies ++= Dependencies.eventStreamsPluginElasticsearch




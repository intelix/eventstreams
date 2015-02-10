import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-retention-mgr")

libraryDependencies ++= Dependencies.eventStreamsPluginElasticsearch




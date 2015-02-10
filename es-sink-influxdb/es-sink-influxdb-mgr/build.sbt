import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-sink-influxdb-mgr")

libraryDependencies ++= Dependencies.eventStreamsPluginInfluxDB




import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-sink-influxdb-service")

libraryDependencies ++= Dependencies.eventStreamsPluginInfluxDB




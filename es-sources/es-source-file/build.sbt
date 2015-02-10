
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-file")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials
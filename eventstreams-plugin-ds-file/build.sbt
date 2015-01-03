
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-plugin-ds-file")


parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsPluginDSEssentials
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-plugin-transactions")

libraryDependencies ++= Dependencies.eventStreamsCommon

parallelExecution in Test := false






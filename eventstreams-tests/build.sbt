
import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-tests")

parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsCommon
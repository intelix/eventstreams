
import au.com.eventstreams.{Dependencies, EventStreamsBuild}

EventStreamsBuild.coreSettings("eventstreams-tests")

parallelExecution in Test := false

libraryDependencies ++= Dependencies.eventStreamsMultinodeTests
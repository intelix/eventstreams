import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("eventstreams-auth-basic-web")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
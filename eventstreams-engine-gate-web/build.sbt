import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("eventstreams-engine-gate-web")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
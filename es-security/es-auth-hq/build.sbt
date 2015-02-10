import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("es-auth-hq")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
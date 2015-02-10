import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("es-gate-hq")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
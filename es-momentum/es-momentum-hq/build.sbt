import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("es-momentum-hq")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("es-alerts-dn-hq")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("es-web-core")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
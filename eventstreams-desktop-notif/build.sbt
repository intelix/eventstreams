import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("eventstreams-desktop-notif")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
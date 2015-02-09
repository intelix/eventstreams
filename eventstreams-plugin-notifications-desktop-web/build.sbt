import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.serviceSettings("eventstreams-plugin-notifications-desktop-web")

libraryDependencies ++= Dependencies.eventStreamsHQ

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
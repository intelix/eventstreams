import eventstreams.{EventStreamsBuild,Dependencies}

//EventStreamsBuild.serviceSettings("es-web-core")

libraryDependencies ++= Dependencies.eventStreamsHQ

libraryDependencies ++= Seq("com.vmunier" %% "play-scalajs-scripts" % "0.1.0")

ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
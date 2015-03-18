import eventstreams.{EventStreamsBuild,Dependencies}

//EventStreamsBuild.settings("es-web-scripts")

libraryDependencies ++= Dependencies.eventStreamsHQ

libraryDependencies += "com.github.japgolly.scalajs-react" %%% "core" % "0.8.2"

libraryDependencies += "com.github.japgolly.scalajs-react" %%% "extra" % "0.8.2"

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.8.0"


ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := ".*routes"
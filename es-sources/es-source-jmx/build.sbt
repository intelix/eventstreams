import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-source-jmx")

resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"

libraryDependencies ++= Dependencies.eventStreamsPluginDSJMX


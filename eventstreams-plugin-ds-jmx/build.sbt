import au.com.eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("eventstreams-plugin-ds-jmx")

resolvers += "JAnalyse Repository" at "http://www.janalyse.fr/repository/"

libraryDependencies ++= Dependencies.eventStreamsPluginDSJMX


import au.com.eventstreams.EventStreamsBuild

EventStreamsBuild.coreSettings("eventstreams")

parallelExecution in Global := false


lazy val coreEvents = Project(
  id = "core-events",
  base = file("core-events")
)


lazy val eventStreamsCore = Project(
  id = "eventstreams-core",
  base = file("eventstreams-core"),
  dependencies = Seq(coreEvents % "compile;test->test")
)

lazy val eventStreamsInstructionsEssentials = Project(
  id = "eventstreams-plugin-instruction-essentials",
  base = file("eventstreams-plugin-instruction-essentials"),
  dependencies = Seq(
    coreEvents % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginEndpointInfluxDB = Project(
  id = "eventstreams-plugin-endpoint-influxdb",
  base = file("eventstreams-plugin-endpoint-influxdb"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsEngines = Project(
  id = "eventstreams-engines",
  base = file("eventstreams-engines"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test",
    eventStreamsInstructionsEssentials,
    eventStreamsPluginEndpointInfluxDB
  )
).enablePlugins(AkkaAppPackaging)


lazy val eventStreamsPluginDSFile = Project(
  id = "eventstreams-plugin-ds-file",
  base = file("eventstreams-plugin-ds-file"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginDSJMX = Project(
  id = "eventstreams-plugin-ds-jmx",
  base = file("eventstreams-plugin-ds-jmx"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginDSStatsd = Project(
  id = "eventstreams-plugin-ds-statsd",
  base = file("eventstreams-plugin-ds-statsd"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginDSUDP = Project(
  id = "eventstreams-plugin-ds-udp",
  base = file("eventstreams-plugin-ds-udp"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginDSTCP = Project(
  id = "eventstreams-plugin-ds-tcp",
  base = file("eventstreams-plugin-ds-tcp"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsAgent = Project(
  id = "eventstreams-agent",
  base = file("eventstreams-agent"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test",
    eventStreamsEngines % "test->test",
    eventStreamsPluginDSFile,
    eventStreamsPluginDSJMX,
    eventStreamsPluginDSStatsd,
    eventStreamsPluginDSUDP,
    eventStreamsPluginDSTCP
  )
)


lazy val eventStreamsHQ = Project(
  id = "eventstreams-hq",
  base = file("eventstreams-hq"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)








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
    eventStreamsCore % "compile;test->test",
    eventStreamsEngineGate % "test->test"
  )
)

lazy val eventStreamsAuthBasic = Project(
  id = "eventstreams-auth-basic",
  base = file("eventstreams-auth-basic"),
  dependencies = Seq(
    coreEvents % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)
lazy val eventStreamsAuthBasicWeb = Project(
  id = "eventstreams-auth-basic-web",
  base = file("eventstreams-auth-basic-web"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)


lazy val eventStreamsPluginSinkInfluxDB = Project(
  id = "eventstreams-plugin-sink-influxdb",
  base = file("eventstreams-plugin-sink-influxdb"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsPluginSinkElasticsearch = Project(
  id = "eventstreams-plugin-sink-elasticsearch",
  base = file("eventstreams-plugin-sink-elasticsearch"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)

lazy val eventStreamsEngineGate = Project(
  id = "eventstreams-engine-gate",
  base = file("eventstreams-engine-gate"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore % "compile;test->test"
  )
)
lazy val eventStreamsEngineFlow = Project(
  id = "eventstreams-engine-flow",
  base = file("eventstreams-engine-flow"),
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
    eventStreamsAuthBasic % "compile;test->test",
    eventStreamsEngineGate % "compile;test->test",
    eventStreamsEngineFlow % "compile;test->test",
    eventStreamsInstructionsEssentials,
    eventStreamsPluginSinkInfluxDB,
    eventStreamsPluginSinkElasticsearch,
    eventStreamsPluginDesktopNotifications
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


lazy val eventStreamsCoreWeb = Project(
  id = "eventstreams-core-web",
  base = file("eventstreams-core-web"),
  dependencies = Seq(
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)

lazy val eventStreamsEngineFlowWeb = Project(
  id = "eventstreams-engine-flow-web",
  base = file("eventstreams-engine-flow-web"),
  dependencies = Seq(
    eventStreamsCoreWeb,
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)

lazy val eventStreamsEngineGateWeb = Project(
  id = "eventstreams-engine-gate-web",
  base = file("eventstreams-engine-gate-web"),
  dependencies = Seq(
    eventStreamsCoreWeb,
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)

lazy val eventStreamsAgentWeb = Project(
  id = "eventstreams-agent-web",
  base = file("eventstreams-agent-web"),
  dependencies = Seq(
    eventStreamsCoreWeb,
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)



lazy val eventStreamsHQ = Project(
  id = "eventstreams-hq",
  base = file("eventstreams-hq"),
  dependencies = Seq(
    eventStreamsAuthBasic,
    eventStreamsCoreWeb,
    eventStreamsAgentWeb,
    eventStreamsEngineFlowWeb,
    eventStreamsEngineGateWeb,
    eventStreamsAuthBasicWeb,
    eventStreamsPluginDesktopNotifications,
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)


lazy val eventStreamsPluginDesktopNotifications = Project(
  id = "eventstreams-plugin-desktop-notif",
  base = file("eventstreams-plugin-desktop-notif"),
  dependencies = Seq(
    eventStreamsCoreWeb,
    coreEvents  % "compile;test->test",
    eventStreamsCore  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)








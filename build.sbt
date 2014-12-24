import au.com.eventstreams.EventStreamsBuild

EventStreamsBuild.coreSettings("eventstreams")

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
)


//
//
//
//lazy val eventStreamsHQWeb = Project(
//  id = "eventstreams-hq-web",
//  base = file("eventstreams-hq/modules/web"),
//  dependencies = Seq(coreEvents, eventStreamsCore)
//).enablePlugins(PlayScala,SbtWeb)
//
//
//lazy val eventStreamsHQ = Project(
//  id = "eventstreams-hq",
//  base = file("eventstreams-hq"),
//  dependencies = Seq(coreEvents, eventStreamsCore)
//).enablePlugins(PlayScala,SbtWeb)
//

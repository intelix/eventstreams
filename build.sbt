import eventstreams.EventStreamsBuild


EventStreamsBuild.coreSettings("eventstreams")

parallelExecution in Global := false


/* Core */

lazy val sysevents = Project(
  id = "es-sysevents",
  base = file("es-core/es-sysevents")
)

lazy val api = Project(
  id = "es-api",
  base = file("es-core/es-api"),
  dependencies = Seq(
    sysevents  % "compile;test->test"
  )
)



/* Agents */

lazy val agent_api = Project(
  id = "es-agent-api",
  base = file("es-agent/es-agent-api"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test"
  )
)

lazy val agent_process = Project(
  id = "es-agent-process",
  base = file("es-agent/es-agent-process"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    node_hub % "test->test",
    gate_service % "compile;test->test",
    agent_api % "compile;test->test",
    agent_service % "compile;test->test",
    source_file % "compile;test->test",
    source_jmx % "compile;test->test",
    source_statsd % "compile;test->test",
    source_udp % "compile;test->test",
    source_elasticsearch % "compile;test->test",
    source_tcp % "compile;test->test"
  )
)

lazy val agent_service = Project(
  id = "es-agent-service",
  base = file("es-agent/es-agent-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    agent_api
  )
)

lazy val agent_hq = Project(
  id = "es-agent-hq",
  base = file("es-agent/es-agent-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    agent_api
  )
).enablePlugins(PlayScala,SbtWeb)




/* Flow */

lazy val flow_service = Project(
  id = "es-flow-service",
  base = file("es-flow/es-flow-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    gate_api,
    instructions_api
  )
)

lazy val flow_hq = Project(
  id = "es-flow-hq",
  base = file("es-flow/es-flow-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    api % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)



/* Gates */

lazy val gate_api = Project(
  id = "es-gate-api",
  base = file("es-gate/es-gate-api"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api
  )
)

lazy val gate_service = Project(
  id = "es-gate-service",
  base = file("es-gate/es-gate-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    gate_api
  )
)

lazy val gate_hq = Project(
  id = "es-gate-hq",
  base = file("es-gate/es-gate-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    gate_api,
    api % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)



/* Security */

lazy val auth_api = Project(
  id = "es-auth-api",
  base = file("es-security/es-auth-api"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    api % "compile;test->test"
  )
)
lazy val auth_service = Project(
  id = "es-auth-service",
  base = file("es-security/es-auth-service"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    api % "compile;test->test",
    auth_api  % "compile;test->test"
  )
)
lazy val auth_hq = Project(
  id = "es-auth-hq",
  base = file("es-security/es-auth-hq"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api % "compile;test->test",
    auth_api  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)




/* Instructions */

lazy val instructions_api = Project(
  id = "es-instructions-api",
  base = file("es-instructions/es-instructions-api"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    api % "compile;test->test",
    gate_service % "test->test"
  )
)
lazy val instructions_set = Project(
  id = "es-instructions-set",
  base = file("es-instructions/es-instructions-set"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    api % "compile;test->test",
    instructions_api % "compile;test->test",
    gate_api % "compile;test->test",
    gate_service % "test->test"
  )
)








/* Interface */

lazy val web_core = Project(
  id = "es-web-core",
  base = file("es-iface/es-web-core"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    auth_api  % "compile;test->test",
    auth_service  % "test->test"
  )
).enablePlugins(PlayScala,SbtWeb)






/* Nodes */

lazy val node_hub = Project(
  id = "es-node-hub",
  base = file("es-nodes/es-node-hub"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    auth_service % "compile;test->test",
    gate_service % "compile;test->test",
    flow_service % "compile;test->test",
    agent_service % "compile;test->test",
    signals_service % "compile;test->test",
    tx_service % "compile;test->test",
    instructions_set % "compile;test->test",
    sink_influxdb_service % "compile;test->test",
    sink_elasticsearch_service % "compile;test->test",
    alerts_dn_service % "compile;test->test"
  )
).enablePlugins(AkkaAppPackaging)


lazy val node_hq = Project(
  id = "es-node-hq",
  base = file("es-nodes/es-node-hq"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    web_core  % "compile;test->test",
    auth_hq  % "compile;test->test",
    agent_hq,
    flow_hq,
    gate_hq,
    alerts_dn_hq
  )
).enablePlugins(PlayScala,SbtWeb)








/* Signals */

lazy val signals_api = Project(
  id = "es-signals-api",
  base = file("es-signals/es-signals-api"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test"
  )
)

lazy val signals_service = Project(
  id = "es-signals-service",
  base = file("es-signals/es-signals-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    signals_api,
    instructions_api,
    instructions_set
  )
)


/* Transactions */

lazy val tx_service = Project(
  id = "es-tx-service",
  base = file("es-tx/es-tx-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    instructions_api  % "compile;test->test",
    instructions_set
  )
)



/* Source - File */

lazy val source_file = Project(
  id = "es-source-file",
  base = file("es-sources/es-source-file"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)

/* Source - JMX */

lazy val source_jmx = Project(
  id = "es-source-jmx",
  base = file("es-sources/es-source-jmx"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)

/* Source - Statsd */

lazy val source_statsd = Project(
  id = "es-source-statsd",
  base = file("es-sources/es-source-statsd"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)

/* Source - UDP */

lazy val source_udp = Project(
  id = "es-source-udp",
  base = file("es-sources/es-source-udp"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)

/* Source - TCP */

lazy val source_tcp = Project(
  id = "es-source-tcp",
  base = file("es-sources/es-source-tcp"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)

/* Source - Elasticsearch */

lazy val source_elasticsearch = Project(
  id = "es-source-elastic",
  base = file("es-sources/es-source-elastic"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    agent_api  % "compile;test->test"
  )
)





/* Sink - InfluxDB */

lazy val sink_influxdb_service = Project(
  id = "es-sink-influxdb-service",
  base = file("es-sink-influxdb/es-sink-influxdb-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    instructions_api  % "compile;test->test"
  )
)


/* Sink - Elasticsearch */

lazy val sink_elasticsearch_service = Project(
  id = "es-sink-elasticsearch-service",
  base = file("es-sink-elasticsearch/es-sink-elasticsearch-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    instructions_api  % "compile;test->test"
  )
)





/* Alerts - Desktop notifications */

lazy val alerts_dn_service = Project(
  id = "es-alerts-dn-service",
  base = file("es-alerts-dn/es-alerts-dn-service"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    api  % "compile;test->test",
    instructions_api % "compile;test->test",
    signals_api % "compile;test->test",
    signals_service % "test->test"
  )
)

lazy val alerts_dn_hq = Project(
  id = "es-alerts-dn-hq",
  base = file("es-alerts-dn/es-alerts-dn-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    api  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)
















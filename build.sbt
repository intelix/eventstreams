import eventstreams.EventStreamsBuild


EventStreamsBuild.coreSettings("eventstreams")

parallelExecution in Global := false


/* Core */

lazy val sysevents = Project(
  id = "es-sysevents",
  base = file("es-core/es-sysevents")
)

lazy val model = Project(
  id = "es-model",
  base = file("es-core/es-model")
)

lazy val core_components = Project(
  id = "es-core-components",
  base = file("es-core/es-core-components"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model
  )
)


/* Agents */

lazy val agent_model = Project(
  id = "es-agent-model",
  base = file("es-agent/es-agent-model"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

lazy val agent_process = Project(
  id = "es-agent-process",
  base = file("es-agent/es-agent-process"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test",
    node_hub % "test->test",
    agent_model,
    source_file,
    source_jmx,
    source_statsd,
    source_udp,
    source_tcp
  )
)

lazy val agent_mgr = Project(
  id = "es-agent-mgr",
  base = file("es-agent/es-agent-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test",
    node_hub % "test->test",
    agent_model
  )
)

lazy val agent_hq = Project(
  id = "es-agent-hq",
  base = file("es-agent/es-agent-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test",
    agent_model
  )
).enablePlugins(PlayScala,SbtWeb)




/* Flow */

lazy val flow_mgr = Project(
  id = "es-flow-mgr",
  base = file("es-flow/es-flow-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

lazy val flow_hq = Project(
  id = "es-flow-hq",
  base = file("es-flow/es-flow-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)



/* Gates */

lazy val gate_mgr = Project(
  id = "es-gate-mgr",
  base = file("es-gate/es-gate-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

lazy val gate_hq = Project(
  id = "es-gate-hq",
  base = file("es-gate/es-gate-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)



/* Security */

lazy val auth_model = Project(
  id = "es-auth-model",
  base = file("es-security/es-auth-model"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)
lazy val auth_mgr = Project(
  id = "es-auth-mgr",
  base = file("es-security/es-auth-mgr"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    core_components % "compile;test->test",
    auth_model  % "compile;test->test"
  )
)
lazy val auth_hq = Project(
  id = "es-auth-hq",
  base = file("es-security/es-auth-hq"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test",
    auth_model  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)




/* Instructions */

lazy val instructions_model = Project(
  id = "es-instructions-model",
  base = file("es-instructions/es-instructions-model"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    model,
    core_components % "compile;test->test",
    gate_mgr % "test->test"
  )
)
lazy val instructions_set = Project(
  id = "es-instructions-set",
  base = file("es-instructions/es-instructions-set"),
  dependencies = Seq(
    sysevents % "compile;test->test",
    model,
    instructions_model % "compile;test->test",
    core_components % "compile;test->test",
    gate_mgr % "test->test"
  )
)








/* Interface */

lazy val web_core = Project(
  id = "es-web-core",
  base = file("es-iface/es-web-core"),
  dependencies = Seq(
    auth_model  % "compile;test->test",
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)






/* Nodes */

lazy val node_hub = Project(
  id = "es-node-hub",
  base = file("es-nodes/es-node-hub"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test",
    auth_mgr % "compile;test->test",
    gate_mgr % "compile;test->test",
    flow_mgr % "compile;test->test",
    instructions_set,
    sink_influxdb_mgr,
    sink_elasticsearch_mgr,
    alerts_dn_mgr
  )
).enablePlugins(AkkaAppPackaging)


lazy val node_hq = Project(
  id = "es-node-hq",
  base = file("es-nodes/es-node-hq"),
  dependencies = Seq(
    auth_hq  % "compile;test->test",
    web_core  % "compile;test->test",
    agent_hq,
    flow_hq,
    gate_hq,
    alerts_dn_hq,
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)





/* Retention */

lazy val retention_mgr = Project(
  id = "es-retention-mgr",
  base = file("es-retention/es-retention-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)



/* Signals */

lazy val signals_mgr = Project(
  id = "es-signals-mgr",
  base = file("es-signals/es-signals-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)


/* Transactions */

lazy val tx_mgr = Project(
  id = "es-tx-mgr",
  base = file("es-tx/es-tx-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)



/* Source - File */

lazy val source_file = Project(
  id = "es-source-file",
  base = file("es-sources/es-source-file"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

/* Source - JMX */

lazy val source_jmx = Project(
  id = "es-source-jmx",
  base = file("es-sources/es-source-jmx"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

/* Source - Statsd */

lazy val source_statsd = Project(
  id = "es-source-statsd",
  base = file("es-sources/es-source-statsd"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

/* Source - UDP */

lazy val source_udp = Project(
  id = "es-source-udp",
  base = file("es-sources/es-source-udp"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

/* Source - TCP */

lazy val source_tcp = Project(
  id = "es-source-tcp",
  base = file("es-sources/es-source-tcp"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)





/* Sink - InfluxDB */

lazy val sink_influxdb_mgr = Project(
  id = "es-sink-influxdb-mgr",
  base = file("es-sink-influxdb/es-sink-influxdb-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)


/* Sink - Elasticsearch */

lazy val sink_elasticsearch_mgr = Project(
  id = "es-sink-elasticsearch-mgr",
  base = file("es-sink-elasticsearch/es-sink-elasticsearch-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)





/* Alerts - Desktop notifications */

lazy val alerts_dn_mgr = Project(
  id = "es-alerts-dn-mgr",
  base = file("es-alerts-dn/es-alerts-dn-mgr"),
  dependencies = Seq(
    sysevents  % "compile;test->test",
    model,
    core_components % "compile;test->test"
  )
)

lazy val alerts_dn_hq = Project(
  id = "es-alerts-dn-hq",
  base = file("es-alerts-dn/es-alerts-dn-hq"),
  dependencies = Seq(
    web_core,
    sysevents  % "compile;test->test",
    model,
    core_components  % "compile;test->test"
  )
).enablePlugins(PlayScala,SbtWeb)
















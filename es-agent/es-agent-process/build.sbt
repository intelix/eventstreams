import com.typesafe.sbt.SbtNativePackager.packageArchetype
import eventstreams.{EventStreamsBuild,Dependencies}

EventStreamsBuild.coreSettings("es-agent-process")

libraryDependencies ++= Dependencies.eventStreamsCore

packageArchetype.akka_application

packageSummary in Linux := "EventStreams Agent"

packageSummary in Windows := "EventStreams Agent"

packageDescription := " EventStreams: http://eventstreams.io"

maintainer in Windows := "Intelix Pty Ltd"

maintainer in Debian := "Max Glukhovtsev <maks@intelix.com.au>"

mainClass in Compile := Some("eventstreams.agent.AgentLauncher")

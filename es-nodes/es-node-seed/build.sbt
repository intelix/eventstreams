import eventstreams.{EventStreamsBuild,Dependencies}
import com.typesafe.sbt.SbtNativePackager.packageArchetype

EventStreamsBuild.coreSettings("es-node-seed")

libraryDependencies ++= Dependencies.eventStreamsEngines

packageArchetype.akka_application

packageSummary in Linux := "EventStreams Seed"

packageSummary in Windows := "EventStreams Seed"

packageDescription := " EventStreams: http://eventstreams.io"

maintainer in Windows := "Intelix Pty Ltd"

maintainer in Debian := "Max Glukhovtsev <maks@intelix.com.au>"

mainClass in Compile := Some("eventstreams.seed.SeedLauncher")

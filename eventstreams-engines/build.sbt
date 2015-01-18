import au.com.eventstreams.{EventStreamsBuild,Dependencies}
import com.typesafe.sbt.SbtNativePackager.packageArchetype

EventStreamsBuild.coreSettings("eventstreams-engines")

libraryDependencies ++= Dependencies.eventStreamsEngines

packageArchetype.akka_application

packageSummary in Linux := "EventStreams Engine"

packageSummary in Windows := "EventStreams Engine"

packageDescription := " EventStreams: http://eventstreams.io"

maintainer in Windows := "Intelix Pty Ltd"

maintainer in Debian := "Max Glukhovtsev <maks@intelix.com.au>"

mainClass in Compile := Some("eventstreams.engine.EngineLauncher")

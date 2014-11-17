import sbt._
import Keys._
import play.PlayImport._
import com.typesafe.sbt.web.SbtWeb.autoImport.{Assets, pipelineStages}
import com.typesafe.sbt.less.Import.LessKeys
import com.typesafe.sbt.rjs.Import.{rjs, RjsKeys}
import com.typesafe.sbt.digest.Import.digest
import com.typesafe.sbt.gzip.Import.gzip

object Common {
  def appName = "ehub"

  // Common settings for every project
  def settings (theName: String) = Seq(
    name := theName,
    organization := "com.myweb",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.2",
    doc in Compile <<= target.map(_ / "none"),
    scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-language:reflectiveCalls")
  )
  // Settings for the app, i.e. the root project
  val appSettings = settings(appName)
  // Settings for every module, i.e. for every subproject
  def moduleSettings (module: String) = settings(module) ++: Seq(
    javaOptions in Test += s"-Dconfig.resource=application.conf"
  )
  // Settings for every service, i.e. for admin and web subprojects
  def serviceSettings (module: String) = moduleSettings(module) ++: Seq(
    includeFilter in (Assets, LessKeys.less) := "*.less",
    excludeFilter in (Assets, LessKeys.less) := "_*.less",
    pipelineStages := Seq(rjs, digest, gzip),
    RjsKeys.mainModule := s"main-$module"
  )

  val commonDependencies = Seq(
    "com.googlecode.scalascriptengine" %% "scalascriptengine" % "1.3.10",
    "org.scala-lang" % "scala-compiler" % "2.11.1",
    "org.codehaus.groovy" % "groovy-all" % "2.3.6",
    "com.typesafe.akka" %% "akka-actor" % "2.3.6",
    "com.typesafe.akka" %% "akka-slf4j" % "2.3.6",
    "com.typesafe.akka" %% "akka-remote" % "2.3.6",
    "com.typesafe.akka" %% "akka-cluster" % "2.3.6",
    "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.6",
    "com.typesafe.akka" %% "akka-stream-experimental" % "0.11",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "org.scalatest" %% "scalatest" % "2.1.3" % "test",
    "joda-time" % "joda-time" % "2.3",
    "org.codehaus.groovy" % "groovy-all" % "2.3.6",
    "org.joda" % "joda-convert" % "1.6",
    "com.typesafe.slick" %% "slick" % "2.1.0",
    "com.h2database" % "h2" % "1.3.166",
    "com.mandubian" %% "play-json-zipper" % "1.2",
    "org.webjars" % "jquery" % "2.1.1",
    "net.ceedubs" %% "ficus" % "1.1.1",
    "org.webjars" % "bootstrap" % "3.2.0",
    "org.webjars" %% "webjars-play" % "2.3.0-2",
    "org.webjars" % "requirejs" % "2.1.14-3",
    "org.webjars" % "requirejs-text" % "2.0.10-1",
    "org.webjars" % "react" % "0.12.0",
    "org.webjars" % "toastr" % "2.1.0",
    "org.scalaz" %% "scalaz-core" % "7.1.0"
  )
}
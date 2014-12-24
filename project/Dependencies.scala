package au.com.eventstreams

import play.PlayImport._
import sbt._

object Dependencies {

  object Versions {
    val scalaVersion = "2.11.4"
    val scalaTestVersion = "2.2.1"

    val playCacheVersion = "2.2.1"
    val playJsonVersion = "2.3.7"

    val playJsonZipperVersion = "1.2"

    val slickVersion = "2.1.0"
    val h2Version = "1.3.166"

    val groovyVersion = "2.3.8"

    val akkaVersion = "2.3.8"
    val akkaStreamVersion = "1.0-M1"

    val scalaLoggingVersion = "3.1.0"
    val logbackVersion = "1.1.2"
    
    val jodaTimeVersion = "2.3"
    val jodaConvertVersion = "1.6"
    val prettytimeVersion = "3.2.5.Final"

    val ficusVersion = "1.1.1"
    
    val scalazVersion = "7.1.0"
    
    val elastic4sVersion = "1.4.0"
    
    val asyncHttpVersion = "1.0.0"

    val metricsScalaVersion = "3.3.0_a2.3"
    
    val uuidVersion = "3.2"
    
    val janalyseJmxVersion = "0.7.1"
    
    val commonsCodecVersion = "1.9"

    val webjarsJqueryVersion = "2.1.3"
    val webjarsPlayVersion = "2.3.0-2"
    val webjarsBWYetiVersion = "3.3.1+2"
    val webjarsBootstrapVersion = "3.3.1"
    val webjarsReqjsVersion = "2.1.14-3"
    val webjarsReqjsTxtVersion = "2.0.10-1"
    val webjarsReactJsVersion = "0.12.1"
    val webjarsToastrVersion = "2.1.0"
    
  }


  object Compile {
    
    import Versions._

    val playCache       = "com.typesafe.play"           %%  "play-cache"                    % playCacheVersion
    val playJson        = "com.typesafe.play"           %%  "play-json"                     % playJsonVersion
    val playJsonZipper  = "com.mandubian"               %%  "play-json-zipper"              % playJsonZipperVersion

    val slick           = "com.typesafe.slick"          %%  "slick"                         % slickVersion
    val h2              = "com.h2database"              %   "h2"                            % h2Version

    val groovy          = "org.codehaus.groovy"         %   "groovy-all"                    % groovyVersion

    val akkaActor       = "com.typesafe.akka"           %% "akka-actor"                     % akkaVersion
    val akkaAgent       = "com.typesafe.akka"           %% "akka-agent"                     % akkaVersion
    val akkaSlf4j       = "com.typesafe.akka"           %% "akka-slf4j"                     % akkaVersion
    val akkaRemote      = "com.typesafe.akka"           %% "akka-remote"                    % akkaVersion
    val akkaCluster     = "com.typesafe.akka"           %% "akka-cluster"                   % akkaVersion
    val akkaPersistence = "com.typesafe.akka"           %% "akka-persistence-experimental"  % akkaVersion
    val akkaStreams     = "com.typesafe.akka"           %% "akka-stream-experimental"       % akkaStreamVersion

    val loggingScala    = "com.typesafe.scala-logging"  %% "scala-logging"                  % scalaLoggingVersion
    val loggingLogback  = "ch.qos.logback"              %  "logback-classic"                % logbackVersion

    val jodaTime        = "joda-time"                   %  "joda-time"                      % jodaTimeVersion
    val jodaConvert     = "org.joda"                    %  "joda-convert"                   % jodaConvertVersion
    val prettyTime      = "org.ocpsoft.prettytime"      %  "prettytime"                     % prettytimeVersion

    val webjarsPlay     = "org.webjars"                 %% "webjars-play"                   % webjarsPlayVersion
    val webjarsJquery   = "org.webjars"                 %  "jquery"                         % webjarsJqueryVersion
    val webjarsBWYeti   = "org.webjars"                 %  "bootswatch-yeti"                % webjarsBWYetiVersion
    val webjarsBootstrap= "org.webjars"                 %  "bootstrap"                      % webjarsBootstrapVersion
    val webjarsReqjs    = "org.webjars"                 %  "requirejs"                      % webjarsReqjsVersion
    val webjarsReqjsTxt = "org.webjars"                 %  "requirejs-text"                 % webjarsReqjsTxtVersion
    val webjarsToastr   = "org.webjars"                 %  "toastr"                         % webjarsToastrVersion
    val webjarsReactJs  = "org.webjars"                 %  "react"                          % webjarsReactJsVersion


    val ficus           = "net.ceedubs"                 %% "ficus"                          % ficusVersion
    val scalaz          = "org.scalaz"                  %% "scalaz-core"                    % scalazVersion
    val elastic4s       = "com.sksamuel.elastic4s"      %% "elastic4s"                      % elastic4sVersion
    val asyncHttpClient = "com.ning"                    %  "async-http-client"              % asyncHttpVersion
    val metricsScala    = "nl.grons"                    %% "metrics-scala"                  % metricsScalaVersion
    val uuid            = "com.eaio.uuid"               %  "uuid"                           % uuidVersion
    val janalyseJmx     = "fr.janalyse"                 %% "janalyse-jmx"                   % janalyseJmxVersion
    val commonsCodec    = "commons-codec"               %  "commons-codec"                  % commonsCodecVersion
  }
  
  object Test {
    import Versions._

    val scalaTest       = "org.scalatest"               %% "scalatest"                      % scalaTestVersion      % "test"
    val akkaMultiNode   = "com.typesafe.akka"           %% "akka-multi-node-testkit"        % akkaVersion           % "test"
    val akkaTestKit   = "com.typesafe.akka"             %% "akka-testkit"                   % akkaVersion           % "test"

  }

  import Compile._



  val essentials = Seq(
    ficus,
    scalaz,
    uuid,
    loggingScala,
    loggingLogback,
    jodaTime,
    jodaConvert,
    prettyTime,
    playJson,
    playJsonZipper,
    Test.scalaTest
  )

  val eventStreamsCommon = essentials ++: Seq(
    akkaActor,
    akkaAgent,
    akkaCluster,
    akkaRemote,
    akkaSlf4j,
    akkaStreams
  )
  
  val eventStreamsCore = eventStreamsCommon ++: Seq(
    commonsCodec,
    metricsScala,
    slick,
    h2
  )

  val eventStreamsEngines = eventStreamsCommon ++: Seq(
    commonsCodec,
    metricsScala,
    elastic4s,
    Test.akkaTestKit
  )
  
  val eventStreamsPluginInstructionsEssentials = eventStreamsCommon ++: Seq(
    groovy
  )
  val eventStreamsPluginInfluxDB = eventStreamsCommon ++: Seq(
    asyncHttpClient
  )

  val eventStreamsPluginDSEssentials = eventStreamsCommon ++: Seq(
  )

  val eventStreamsPluginDSJMX = eventStreamsCommon ++: Seq(
    janalyseJmx
  )

  val eventStreamsAgent = eventStreamsCommon ++: Seq(
    Test.akkaTestKit
  )

}


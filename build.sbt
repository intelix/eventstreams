Common.appSettings

lazy val core = (project in file("modules/core"))

lazy val common = (project in file("modules/common"))
  .enablePlugins(SbtWeb)
  .enablePlugins(PlayScala)
  .dependsOn(core)

lazy val admin = (project in file("modules/admin"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(common, core)

lazy val web = (project in file("modules/web"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(common, core)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .aggregate(core, common, admin, web)
  .dependsOn(core, common, admin, web)

libraryDependencies ++= Common.commonDependencies
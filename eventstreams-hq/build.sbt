

Common.moduleSettings("eventstreams-hq")


//lazy val common = (project in file("modules/common"))
//  .enablePlugins(SbtWeb)
//  .enablePlugins(PlayScala)
//  .dependsOn(coreEvents)
//
//lazy val admin = (project in file("modules/admin"))
//  .enablePlugins(PlayScala)
//  .enablePlugins(SbtWeb)
//  .dependsOn(common)



//  .aggregate(common, admin, web, eventStreamsCore)
//  .dependsOn(common, admin, web, eventStreamsCore)

libraryDependencies ++= Common.commonDependencies

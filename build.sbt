import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

Common.appSettings

lazy val coreEvents = RootProject(file("../core-events"))
lazy val eventStreamsCore = RootProject(file("../eventstreams-core"))

//lazy val common = (project in file("modules/common"))
//  .enablePlugins(SbtWeb)
//  .enablePlugins(PlayScala)
//  .dependsOn(coreEvents)
//
//lazy val admin = (project in file("modules/admin"))
//  .enablePlugins(PlayScala)
//  .enablePlugins(SbtWeb)
//  .dependsOn(common)

lazy val web = (project in file("modules/web"))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .dependsOn(eventStreamsCore)
//  .dependsOn(common, eventStreamsCore)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .aggregate(web, eventStreamsCore)
  .dependsOn(web, eventStreamsCore)
//  .aggregate(common, admin, web, eventStreamsCore)
//  .dependsOn(common, admin, web, eventStreamsCore)

libraryDependencies ++= Common.commonDependencies

licenseOverrides := {
  case DepModuleInfo(_, "prettytime", _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("cglib", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("com.fasterxml.jackson.core", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("com.google.guava", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("com.h2database", _, _) => LicenseInfo(LicenseCategory.Mozilla, "MPL 2.0", "http://www.mozilla.org/MPL/2.0")
  case DepModuleInfo("com.typesafe.play", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("commons-codec", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("commons-collections", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("commons-io", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("commons-logging", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("fr.janalyse", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("io.dropwizard.metrics", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("log4j", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("oauth.signpost", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.antlr", _, _) => LicenseInfo(LicenseCategory.BSD, "BSD", "https://github.com/antlr/antlr4/blob/master/LICENSE.txt")
  case DepModuleInfo("org.apache.commons", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.easytesting", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.apache.httpcomponents", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.apache.lucene", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.eclipse.jetty", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.fusesource.hawtjni", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.fusesource.leveldbjni", _, _) => LicenseInfo(LicenseCategory.BSD, "New BSD", "http://opensource.org/licenses/BSD-3-Clause")
  case DepModuleInfo("org.hamcrest", _, _) => LicenseInfo(LicenseCategory.BSD, "BSD", "http://opensource.org/licenses/BSD-3-Clause")
  case DepModuleInfo("org.ow2.asm", _, _) => LicenseInfo(LicenseCategory.BSD, "BSD", "http://asm.ow2.org/asmdex-license.html")
  case DepModuleInfo("org.iq80.leveldb", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.json", _, _) => LicenseInfo(LicenseCategory.NoneSpecified, "???", "http://www.json.org/license.html")
  case DepModuleInfo("org.reactivestreams", _, _) => LicenseInfo(LicenseCategory.PublicDomain, "CC0", "http://creativecommons.org/publicdomain/zero/1.0/")
  case DepModuleInfo("org.seleniumhq.selenium", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
  case DepModuleInfo("org.slf4j", _, _) => LicenseInfo(LicenseCategory.MIT, "MIT", "http://www.slf4j.org/license.html")
  case DepModuleInfo("org.w3c.css", _, _) => LicenseInfo(LicenseCategory.GPL, "GPL", "http://www.w3.org/Consortium/Legal/copyright-software-19980720")
  case DepModuleInfo(_, "bootswatch-cerulean", _) => LicenseInfo(LicenseCategory.MIT, "MIT", "https://github.com/thomaspark/bootswatch/blob/gh-pages/LICENSE")
  case DepModuleInfo(_, "requirejs", _) => LicenseInfo(LicenseCategory.MIT, "MIT", "http://www.opensource.org/licenses/mit-license.php")
  case DepModuleInfo("xalan", _, _) => LicenseInfo(LicenseCategory.Apache, "Apache 2", "http://www.apache.org/licenses/LICENSE-2.0")
}

name := """RepChain"""

version := "1.1.0"

scalaVersion := "2.12.10"

lazy val akkaVersion = "2.6.1"
val akkaHttpVersion   = "10.1.11"

dependencyOverrides ++= Seq(
  "org.json4s" % "json4s-jackson" % "3.6.7",
  "com.google.guava" % "guava" % "21.0",
  "com.thesamet.scalapb" % "scalapb-runtime" % "0.10.0-M2",
  "org.scala-lang.modules" % "scala-xml" % "2.0.0-M1"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
 // "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
)

libraryDependencies += "com.typesafe.akka" %% "akka-http-xml" % "10.2.9"
  //akkaHttpVersion
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

libraryDependencies += "com.thesamet.scalapb" %% "scalapb-json4s" % "0.10.1-M1"

resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += ("com.gitee.BTAJL" % "RCJava-core" % "v2.0.0").exclude("org.bouncycastle", "bcprov-jdk15on")

libraryDependencies += "org.iq80.leveldb" % "leveldb" % "0.12"
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

libraryDependencies += "org.rocksdb" % "rocksdbjni" % "6.4.6"

libraryDependencies += "org.mapdb" % "mapdb" % "3.0.7"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.8"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"

libraryDependencies += "org.javadelight" % "delight-nashorn-sandbox" % "0.1.27"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

//libraryDependencies += "com.gilt" %% "gfc-timeuuid" % "0.0.8"
libraryDependencies += "io.netty" % "netty" % "3.10.6.Final"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.3.0"

libraryDependencies += "org.codehaus.janino" % "janino" % "3.0.12"

//libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.61"
libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.1"

//add erasurecode for java
libraryDependencies += "org.apache.hadoop" % "hadoop-common" % "3.2.0"
//libraryDependencies += "org.python" % "jython" % "2.7.2"
libraryDependencies += "org.javatuples" % "javatuples" % "1.2"
//add java encrpto for bc
libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.67"
libraryDependencies += "cglib" % "cglib" % "3.3.0"

libraryDependencies ++= Seq(
//  "io.swagger" % "swagger-jaxrs" % "1.6.0",
//  "com.github.swagger-akka-http" %% "swagger-akka-http" % "1.1.1",
//  "io.swagger.core.v3" % "swagger-jaxrs2" % "2.1.5",
  "javax.ws.rs" % "javax.ws.rs-api" % "2.0.1",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.0.4",
//  "io.swagger" % "swagger-jersey2-jaxrs" % "1.6.0",
  "com.typesafe.akka" %% "akka-http" % "10.2.9",
//akkaHttpVersion  "10.2.9"
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.9",
    //akkaHttpVersion,
  "org.json4s" %% "json4s-native" % "3.6.7",
  "org.json4s" %% "json4s-jackson" % "3.6.7",

  "ch.megard" %% "akka-http-cors" % "0.4.0",
  "de.heikoseeberger" % "akka-http-json4s_2.12" % "1.26.0",
  "com.twitter" %% "chill-akka" % "0.9.4",
  "com.twitter" % "chill-bijection_2.12" % "0.9.4",
  "joda-time" % "joda-time" % "2.10.14"
)

System.getProperty("os.name").toLowerCase match {
  case mac if mac.contains("mac") =>
    Compile / unmanagedJars ++= {
      val base = baseDirectory.value
      System.out.println("########base="+base)
      val baseDirectories = (base / "custom_lib") //+++ (base / "b" / "lib") +++ (base / "libC")
      System.out.println("########baseDirectories="+baseDirectories)
      val customJars = (baseDirectories ** "wasmer-jni-amd64-darwin-0.3.0.jar")// +++ (base / "d" / "my.jar")
      System.out.println("########customJars="+customJars)
      System.out.println("########classpath=" + customJars.classpath)
      customJars.classpath
    }

  case win if win.contains("win") =>
    Compile / unmanagedJars ++= {
      val base = baseDirectory.value
      System.out.println("########base=" + base)
      val baseDirectories = (base / "custom_lib") //+++ (base / "b" / "lib") +++ (base / "libC")
      System.out.println("########baseDirectories=" + baseDirectories)
      val customJars = (baseDirectories ** "wasmer-jni-amd64-windows-0.3.0.jar") // +++ (base / "d" / "my.jar")
      System.out.println("########customJars=" + customJars)
      System.out.println("########classpath=" + customJars.classpath)
      customJars.classpath
    }
  case linux if linux.contains("linux") =>
    Compile / unmanagedJars ++= {
      val base = baseDirectory.value
      System.out.println("########base=" + base)
      val baseDirectories = (base / "custom_lib")
      System.out.println("########baseDirectories=" + baseDirectories)
      val customJars = (baseDirectories ** "wasmer-jni-amd64-linux-0.3.0.jar")
      System.out.println("########customJars=" + customJars)
      System.out.println("########classpath=" + customJars.classpath)
      customJars.classpath
    }
  case osName => throw new RuntimeException(s"Unknown operating system $osName")
}



javacOptions ++= Seq("-encoding", "UTF-8")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
//addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")
//scalacOptions += "-P:linter:disable:UseIfExpression+VariableAssignedUnusedValue+UseGetOrElseNotPatMatch"
//scapegoatVersion in ThisBuild := "1.3.3"
//scapegoatDisabledInspections := Seq("OptionGet", "AsInstanceOf","MethodReturningAny")
libraryDependencies += "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2"

excludeDependencies ++= Seq(
  ExclusionRule("org.slf4j", "slf4j-log4j12")
)

assemblyMergeStrategy in assembly := {
  case PathList("org", "iq80", "leveldb", xs @ _*) => MergeStrategy.first
  case PathList("javax", "ws", "rs", xs @ _*) => MergeStrategy.first
  case PathList("module-info.class") => MergeStrategy.discard
  case PathList("google", "protobuf", "field_mask.proto") => MergeStrategy.discard

  case PathList("org", "slf4j", xs @ _*)         => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "axiom.xml" => MergeStrategy.filterDistinctLines
  case PathList(ps @ _*) if ps.last endsWith "Log$Logger.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "ILoggerFactory.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticLoggerBinder.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticMDCBinder.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "StaticMarkerBinder.class" => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith "module-info.class" => MergeStrategy.discard

  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

fork in run := true
javaOptions in run ++= Seq("-Dlogback.configurationFile=conf/logback.xml")

mainClass in (Compile, run) := Some("rep.app.Repchain_Single")
mainClass in (Compile, packageBin) := Some("rep.app.Repchain_Single")
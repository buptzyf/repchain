name := """RepChain"""

version := "0.1"

scalaVersion := "2.11.11"

lazy val akkaVersion = "2.5.3"
val akkaHttpVersion   = "10.0.9"



libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
  )

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
libraryDependencies += "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"

libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
// For ScalaPB 0.6.x:
libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-json4s" % "0.3.0"

libraryDependencies += "org.iq80.leveldb" % "leveldb" % "0.7"
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

libraryDependencies += "org.mapdb" % "mapdb" % "2.0-beta13"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

libraryDependencies += "org.javadelight" % "delight-nashorn-sandbox" % "0.0.10"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

libraryDependencies += "com.gilt" %% "gfc-timeuuid" % "0.0.8"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "org.codehaus.janino" % "janino" % "2.6.1"

libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.57"

libraryDependencies += "org.eclipse.californium" % "californium-core" % "2.0.0-M11"

libraryDependencies ++= Seq(
  "io.swagger" % "swagger-jaxrs" % "1.5.13",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.9.1",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "de.heikoseeberger" % "akka-http-json4s_2.11" % "1.16.1",
  "org.json4s" %% "json4s-native" % "3.5.4",
  "org.json4s" %% "json4s-jackson" % "3.5.4",

  "ch.megard" %% "akka-http-cors" % "0.2.2"
)	

javacOptions ++= Seq("-encoding", "UTF-8")

mainClass in (Compile, packageBin) := Some("rep.app.Repchain")